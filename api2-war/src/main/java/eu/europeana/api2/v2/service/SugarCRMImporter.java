package eu.europeana.api2.v2.service;

import java.io.StringWriter;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Element;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Morphia;
import com.google.code.morphia.query.Query;
import com.google.code.morphia.query.UpdateOperations;
import com.google.common.collect.Lists;
import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.MongoException;

import eu.europeana.api2.v2.model.json.sugarcrm.DataSet;
import eu.europeana.api2.v2.model.json.sugarcrm.Provider;
import eu.europeana.api2.v2.model.json.sugarcrm.SugarCRMSearchResults;
import eu.europeana.corelib.logging.Log;
import eu.europeana.corelib.logging.Logger;
import eu.europeana.uim.sugarcrmclient.enums.EuropeanaDatasets;
import eu.europeana.uim.sugarcrmclient.enums.EuropeanaRetrievableField;
import eu.europeana.uim.sugarcrmclient.enums.EuropeanaUpdatableField;
import eu.europeana.uim.sugarcrmclient.internal.helpers.ClientUtils;
import eu.europeana.uim.sugarcrmclient.jibxbindings.GetEntryList;
import eu.europeana.uim.sugarcrmclient.jibxbindings.GetEntryListResponse;
import eu.europeana.uim.sugarcrmclient.jibxbindings.GetRelationships;
import eu.europeana.uim.sugarcrmclient.jibxbindings.GetRelationshipsResponse;
import eu.europeana.uim.sugarcrmclient.jibxbindings.SelectFields;
import eu.europeana.uim.sugarcrmclient.ws.SugarWsClient;
import eu.europeana.uim.sugarcrmclient.ws.exceptions.JIXBQueryResultException;

public class SugarCRMImporter {

	@Log
	private Logger log;

	@Resource(name = "api_db_mongo_cache")
	private Mongo mongo;

	@Resource
	private SugarWsClient sugarwsClient;

	@Resource
	private SugarCRMCache sugarCRMCache;

	@Resource(name = "corelib_db_morphia_datastore_sugarcrmcache")
	private Datastore datastore;

	private final static String CACHEDB = "sugarcrmCache";

	private final static String DATA_AGGREGATOR_QUERY = "(accounts_cstm.agg_status_c LIKE '%D')";
	private final static String CONTENT_PROVIDER_QUERY = "(accounts_cstm.agg_status_c LIKE '%P')";
	private final static String ALL_PROVIDER_QUERY = String.format("(%s OR %s)",
			DATA_AGGREGATOR_QUERY, CONTENT_PROVIDER_QUERY);

	/**
	 * Performs a re-population/synchronization of the MongoDB cache if found that
	 * the latter is empty.
	 * 
	 * @throws JIXBQueryResultException
	 */
	public void populateRepositoryFromScratch() throws JIXBQueryResultException {
		SugarCRMSearchResults<Provider> providers = sugarCRMCache.getProviders();
		if (providers.items.isEmpty()) {

			ArrayList<Element> exportedProviders = Lists.newArrayListWithExpectedSize(1000);
			GetEntryList providerRequest = new GetEntryList();

			// We want to retrieve all fields
			SelectFields fields = new SelectFields();
			providerRequest.setSelectFields(fields);
			providerRequest.setModuleName(EuropeanaDatasets.ORGANIZATIONS.getSysId());
			providerRequest.setSession(sugarwsClient.getSessionID());
			providerRequest.setOrderBy(
				EuropeanaRetrievableField.DATE_ENTERED.getFieldId());
			providerRequest.setMaxResults(100);
			providerRequest.setOffset(0);
			providerRequest.setQuery(DATA_AGGREGATOR_QUERY);

			int offset = 0;
			while (true) {
				GetEntryListResponse response = sugarwsClient.getEntryList(providerRequest);

				if (response.getReturn().getEntryList().getArray() != null) {
					exportedProviders.addAll((ArrayList<Element>) response.getReturn()
						.getEntryList().getArray().getAnyList());
				} else {
					break;
				}
				offset = response.getReturn().getNextOffset();
				providerRequest.setOffset(offset);
			}

			log.info(String.format("Saving %d providers", exportedProviders.size()));
			int i = 1;
			for (Element exportedProvider : exportedProviders) {
				log.info(String.format("Processing provider %d/%d", i++, exportedProviders.size()));
				Provider provider = new Provider();
				populateProviderFromDOMElement(provider, exportedProvider);
				datastore.save(provider);
				extractDatasetsFromProvider(exportedProvider);
			}
			log.info("All providers are saved.");
		}
	}

	/**
	 * Auxiliary Method for retrieving all the providers (from SugarCRM)
	 * 
	 * @return the JSON/Morphia annotated provider beans wrapped in a SugarCRMSearchResults JSON object
	 * @throws JIXBQueryResultException
	 */
	private SugarCRMSearchResults<Provider> retrieveProviders(String... query)
			throws JIXBQueryResultException {

		GetEntryList request = new GetEntryList();
		SugarCRMSearchResults<Provider> results = new SugarCRMSearchResults<Provider>("", "");
		results.items = new ArrayList<Provider>();
		request.setSelectFields(new SelectFields());
		request.setModuleName(EuropeanaDatasets.ORGANIZATIONS.getSysId());
		request.setSession(sugarwsClient.getSessionID());
		request.setOrderBy(EuropeanaRetrievableField.DATE_ENTERED.getFieldId());
		request.setMaxResults(200);
		request.setOffset(0);

		if (query.length == 0) {
			request.setQuery(DATA_AGGREGATOR_QUERY);
		}
		else {
			StringWriter querywrt = new StringWriter();
			if (query.length == 1) {
				querywrt.append("(");
				querywrt.append(query[0]);
				querywrt.append(")");
				request.setQuery(querywrt.toString());
			}
			else {
				querywrt.append("(");

				for (int i=0; i<query.length; i++) {
					querywrt.append("(");
					querywrt.append(query[i]);
					querywrt.append(")");
					if (i < query.length -1) {
						querywrt.append(" and ");
					}
				}

				querywrt.append(")");
				request.setQuery(querywrt.toString());
			}
		}

		boolean getNext = true;
		while (getNext == true) {
			GetEntryListResponse response = sugarwsClient.getEntryList(request);
			ArrayList<Element> xmlElements = null;

			if (response.getReturn().getEntryList().getArray() != null) {
				xmlElements = (ArrayList<Element>) response.getReturn().getEntryList().getArray().getAnyList();
			} else {
				xmlElements = new ArrayList<Element>();
			}

			log.info(String.format("Retrieved %d providers", xmlElements.size()));
			for (Element xmlElement : xmlElements) {
				Provider provider = new Provider();
				// Insert values in Provider Object
				populateProviderFromDOMElement(provider, xmlElement);
				results.items.add(provider);
			}
			if (response.getReturn().getResultCount() > 0
				&& response.getReturn().getNextOffset() > 0) {
				request.setOffset(response.getReturn().getNextOffset());
			} else {
				getNext = false;
			}
		}

		return results;
	}

	/**
	 * Auxiliary method that extracts and populates the Mongo cache with all the Datasets
	 * that belong to a provider given the provider's DOM represention contained in the
	 * web service response.
	 * 
	 * @param exportedProvider The provider DOM element
	 * @throws JIXBQueryResultException
	 */
	private void extractDatasetsFromProvider(Element exportedProvider)
			throws JIXBQueryResultException {

		String sugarCRMProviderID = ClientUtils.extractFromElement("id", exportedProvider);
		String uimProviderID = ClientUtils.extractFromElement("name_id_c", exportedProvider);
		GetRelationships request = new GetRelationships();
		request.setDeleted(0);
		request.setModuleId(sugarCRMProviderID);
		request.setModuleName("Accounts");
		request.setRelatedModule("Opportunities");
		request.setRelatedModuleQuery("");
		request.setSession(sugarwsClient.getSessionID());

		GetRelationshipsResponse resp = sugarwsClient.getRelationships(request);
		if (resp.getReturn().getIds().getArray() != null) {
			List<Element> el = resp.getReturn().getIds().getArray()
					.getAnyList();

			// Iterate the retrieved related datasets
			for (Element elm : el) {
				String datasetId = extractDatasetId(elm.getTextContent());
				if (StringUtils.isNotBlank(datasetId)) {
					String query = "opportunities.id LIKE '" + datasetId + "%'";

					SugarCRMSearchResults<DataSet> datasets = retrieveDataset(query, uimProviderID); 
					if (datasets.items.size() != 0) {
						for (DataSet dataSet : datasets.items) {
							datastore.save(dataSet);
						}
					}
				}
			}
		}
	}

	/**
	 * Basic polling function for providers (sugarcrm 2 cache)
	 * @throws JIXBQueryResultException 
	 */
	public SugarCRMSearchResults<Provider> pollProviders() throws JIXBQueryResultException {
		log.info("pollProviders()");
		String q1 = "accounts.date_modified > " + getLastProviderModification(); // DATE_SUB(NOW(),INTERVAL 66 MINUTE)
		String q2 = "accounts_cstm.agg_status_c LIKE '%D'";
		log.info("query: " + q1);
		SugarCRMSearchResults<Provider> retrievedProviders = retrieveProviders(q1, q2);
		log.info(String.format("retrievedProviders: %d", retrievedProviders.items.size()));
		for (Provider provider : retrievedProviders.items) {
			log.info(String.format("Provider: %s was updated by the scheduler...", provider.identifier));
			saveupdateProvider2Cache(provider);
		}
		return retrievedProviders;
	}

	/**
	 * Basic polling function for datasets (sugarcrm 2 cache)
	 * @throws JIXBQueryResultException
	 * 
	 * db.DataSet.find({},{"savedsugarcrmFields.date_modified": 1, "_id": 0}).sort({"savedsugarcrmFields.date_modified": -1}).limit(1);
	 */
	public SugarCRMSearchResults<DataSet> pollCollections() throws JIXBQueryResultException {
		String query = "opportunities.date_modified > " + getLastDatasetModification(); // DATE_SUB(NOW(),INTERVAL 66 MINUTE)"
		log.info("pollCollections(); query: " + query);
		SugarCRMSearchResults<DataSet> retrievedDatasets = retrieveDataset(query, null);
		log.info(String.format("retrievedDatasets: %d", retrievedDatasets.items.size()));
		for (DataSet dataset : retrievedDatasets.items) {
			log.info(String.format("Dataset: %s was updated by the scheduler at %s", dataset.identifier, dataset.savedsugarcrmFields.get("date_modified")));
			saveupdateCollection2Cache(dataset);
		}
		return retrievedDatasets;
	}

	public String getLastProviderModification() {
		Query<Provider> query = datastore.find(Provider.class);
		query.field("savedsugarcrmFields.date_modified");
		query.order("-savedsugarcrmFields.date_modified");
		query.limit(1);
		List<Provider> providers = query.asList();
		if (providers != null && providers.size() > 0) {
			return '"' + providers.get(0).savedsugarcrmFields.get(
				EuropeanaRetrievableField.DATE_MODIFIED.getFieldId()) + '"';
		}
		return null;
	}

	public String getLastDatasetModification() {
		Query<DataSet> query = datastore.find(DataSet.class);
		query.field("savedsugarcrmFields.date_modified");
		query.order("-savedsugarcrmFields.date_modified");
		query.limit(1);
		List<DataSet> dataSets = query.asList();
		if (dataSets != null && dataSets.size() > 0) {
			return '"' + dataSets.get(0).savedsugarcrmFields.get(
				EuropeanaRetrievableField.DATE_MODIFIED.getFieldId()) + '"';
		}
		return null;
	}

	/**
	 * Auxiliary Method used to retrieve datasets by ID (from SugarCRM)
	 * @param id 
	 * @return the JSON/Morphia annotated dataset beans wrapped in a SugarCRMSearchResults JSON object
	 * @throws JIXBQueryResultException
	 */
	private SugarCRMSearchResults<DataSet> retrieveDataset(String query, String providerID) 
			throws JIXBQueryResultException {
		SugarCRMSearchResults<DataSet> results = new SugarCRMSearchResults<DataSet>("", "");
		results.items = new ArrayList<DataSet>();

		GetEntryList request = new GetEntryList();
		// We want to retrieve all fields
		SelectFields fields = new SelectFields();
		request.setSelectFields(fields);
		request.setModuleName(EuropeanaDatasets.DATASETS.getSysId());
		request.setSession(sugarwsClient.getSessionID());
		request.setOrderBy(EuropeanaRetrievableField.DATE_ENTERED.getFieldId());
		request.setMaxResults(100);
		request.setOffset(0);
		request.setQuery(query);

		boolean getNext = true;
		while (getNext == true) {
			GetEntryListResponse response = sugarwsClient.getEntryList(request);
			ArrayList<Element> xmlElements = null;
			if (response.getReturn().getEntryList().getArray() != null) {
				xmlElements = (ArrayList<Element>) response.getReturn().getEntryList().getArray().getAnyList();
			} else {
				xmlElements = new ArrayList<Element>();
			}

			for (Element xmlElement : xmlElements) {
				DataSet dataSet = new DataSet();
				populateDatasetFromDOMElement(dataSet, xmlElement, providerID);
				results.items.add(dataSet);
			}

			if (response.getReturn().getResultCount() > 0
				&& response.getReturn().getNextOffset() > 0) {
				request.setOffset(response.getReturn().getNextOffset());
			} else {
				getNext = false;
			}
		}
		return results;
	}

	/**
	 * Auxiliary method that populates a Morphia annotated DataSet object 
	 * given a received DOM element
	 * 
	 * @param prov a reference to the DataSet object 
	 * @param xmlElement a reference to the DOM element
	 */
	private void populateDatasetFromDOMElement(DataSet dataSet, Element xmlElement, String providerID) {
		String identifier = ClientUtils.extractFromElement(
				EuropeanaRetrievableField.NAME.getFieldId(), xmlElement).split("_")[0];
		dataSet.identifier = identifier;
		dataSet.provIdentifier = providerID;
		dataSet.status = ClientUtils.translateStatus(ClientUtils.extractFromElement(
				EuropeanaUpdatableField.STATUS.getFieldId(), xmlElement));
		dataSet.edmDatasetName = ClientUtils.extractFromElement(
				EuropeanaRetrievableField.NAME.getFieldId(), xmlElement);
		String publishedRecordsStr = ClientUtils.extractFromElement(
				EuropeanaUpdatableField.TOTAL_INGESTED.getFieldId(), xmlElement);
		if (StringUtils.isNotBlank(publishedRecordsStr)) {
			try {
				dataSet.publishedRecords = Long.parseLong(publishedRecordsStr);
			} catch (NumberFormatException e) {
				dataSet.publishedRecords = 0;
			}
		} else {
			dataSet.publishedRecords = 0;
		}
		String numberOfDeletedRecords = ClientUtils.extractFromElement(
				EuropeanaUpdatableField.DELETED_RECORDS.getFieldId(), xmlElement);
		if (StringUtils.isNotBlank(numberOfDeletedRecords)) {
			try {
				dataSet.deletedRecords = Long.parseLong(numberOfDeletedRecords);
			} catch (NumberFormatException e) {
				dataSet.deletedRecords = 0;
			}
		} else {
			dataSet.deletedRecords = 0;
		}
		dataSet.savedsugarcrmFields = ClientUtils.mapFromElement(xmlElement);
	}

	/**
	 * Copy the fields needed from the DOM element to the related 
	 * (persisted) Morphia annotated fields. 
	 * 
	 * @param provider the provider object
	 * @param xmlElement the DOM element
	 */
	private void populateProviderFromDOMElement(Provider provider, Element xmlElement) {
		provider.identifier = ClientUtils.extractFromElement("name_id_c", xmlElement);
		provider.country = ClientUtils.extractFromElement("country_c", xmlElement);
		provider.savedsugarcrmFields = ClientUtils.mapFromElement(xmlElement);
	}

	/**
	 * Removes timestamp from dataset ID.
	 * For example:
	 * 7dd9673a-41e7-880c-4cb1-5101299709812014-01-15 10:13:490 --> 7dd9673a-41e7-880c-4cb1-51012997098
	 * @param rawId
	 * @return
	 */
	private String extractDatasetId(String rawId) {
		if (StringUtils.isBlank(rawId)) {
			return null;
		}
		String[] datasetIdParts = rawId.split("-");
		StringBuilder datasetId = new StringBuilder();
		for (int i = 0; i < 5; i++) {
			if (i < 4) {
				datasetId.append(datasetIdParts[i]);
				datasetId.append("-");
			} else {
				datasetId.append(datasetIdParts[i].substring(0, 11));
			}
		}
		return datasetId.toString();
	}

	/**
	 * Auxiliary method that saves or updates a Dataset into the Cache (MongoDB)
	 * @param dataSet the collection object
	 */
	public void saveupdateCollection2Cache(DataSet dataSet) {
		UpdateOperations<DataSet> operations = 
			datastore.createUpdateOperations(DataSet.class)
				.disableValidation()
					.set("savedsugarcrmFields", dataSet.savedsugarcrmFields);
		Query<DataSet> query = datastore.createQuery(DataSet.class)
				.field("identifier").equal(dataSet.identifier);
		datastore.updateFirst(query, operations, true);
	}

	/**
	 * Auxiliary method that saves or updates a Provider into the Cache (MongoDB)
	 * @param provider the provider object
	 */
	public void saveupdateProvider2Cache(Provider provider) {
		UpdateOperations<Provider> operations = 
			datastore.createUpdateOperations(Provider.class)
				.disableValidation()
					.set("savedsugarcrmFields", provider.savedsugarcrmFields);
		Query<Provider> query = datastore.createQuery(Provider.class)
				.field("identifier").equal(provider.identifier);
		datastore.updateFirst(query, operations, true);
	}

	/**
	 * Setter for SugarWsClient instance
	 * @param sugarwsClient
	 */
	public void setSugarwsClient(SugarWsClient sugarwsClient) {
		this.sugarwsClient = sugarwsClient;
	}

	/**
	 * Use a local instance if MongoDB version cannot be injected from Spring 
	 * from Spring Context (useful in Unit Testing)
	 */
	@PostConstruct
	public void initLocal() {
		if (datastore == null) {
			log.info("SugarCRMCache datasource is null");
			if (mongo == null) {
				log.info("SugarCRMCache mongo is null");
				try {
					mongo = new Mongo();
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (MongoException e) {
					e.printStackTrace();
				}
			}

			DB db = mongo.getDB(CACHEDB);
			Morphia morphia = new Morphia();
			morphia.map(DataSet.class).map(Provider.class);
			datastore = morphia.createDatastore(mongo, CACHEDB);
		}

		datastore.getDB().getCollection("DataSet").ensureIndex("identifier");
		datastore.getDB().getCollection("DataSet").ensureIndex("savedsugarcrmFields.name");
		datastore.getDB().getCollection("DataSet").ensureIndex("savedsugarcrmFields.country_c");
		datastore.getDB().getCollection("DataSet").ensureIndex("savedsugarcrmFields.sales_stage");
		datastore.getDB().getCollection("Provider").ensureIndex("identifier");
	}

}
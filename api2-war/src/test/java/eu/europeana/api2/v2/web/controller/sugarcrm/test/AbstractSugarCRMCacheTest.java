/*
 * Copyright 2007-2015 The Europeana Foundation
 *
 * Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved
 * by the European Commission;
 * You may not use this work except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * http://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, without warranties or conditions of
 * any kind, either express or implied.
 * See the Licence for the specific language governing permissions and limitations under
 * the Licence.
 */
package eu.europeana.api2.v2.web.controller.sugarcrm.test;

import static org.junit.Assert.*;

import org.junit.Test;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import eu.europeana.api2.v2.model.json.sugarcrm.DataSet;
import eu.europeana.api2.v2.model.json.sugarcrm.Provider;
import eu.europeana.api2.v2.model.json.sugarcrm.SugarCRMSearchResults;
import eu.europeana.api2.v2.service.SugarCRMCache;
import eu.europeana.api2.v2.service.SugarCRMImporter;
import eu.europeana.uim.sugarcrmclient.ws.SugarWsClient;
import eu.europeana.uim.sugarcrmclient.ws.exceptions.JIXBQueryResultException;

/**
 * Unit Tests for API caching mechanism (actual contenet executed by both Mock &
 * Integration Tests)
 * 
 * @author Georgios Markakis (gwarkx@hotmail.com)
 *
 * @since Nov 4, 2013
 */
public abstract class AbstractSugarCRMCacheTest {

	protected static MongodExecutable mongodExe;
	protected static MongodProcess mongod;
	protected static SugarCRMCache cacheInstance;
	protected static SugarCRMImporter importerInstance;
	protected static SugarWsClient sugarwsClient;

	/**
	 * @throws JIXBQueryResultException
	 */
	@Test
	public void populationTest() throws JIXBQueryResultException {
		importerInstance.populateRepositoryFromScratch();
	}

	/**
	 * Get provider by ID
	 */
	@Test
	public void getProviderbyIDTest() {
		String id = "916";
		SugarCRMSearchResults<Provider> provres = cacheInstance.getProviderbyID(id);
		assertTrue(!provres.items.isEmpty());
		Provider prov = provres.items.get(0);
		assertNotNull(prov);
		assertNotNull(prov.savedsugarcrmFields);
		assertEquals(id, prov.identifier);
	}

	/**
	 * Get providers
	 */
	@Test
	public void getProvidersTest() {
		SugarCRMSearchResults<Provider> provs = cacheInstance.getProviders();
		assertNotNull(provs.items);
		assertTrue(!provs.items.isEmpty());

		for (Provider prov : provs.items) {
			assertNotNull(prov.identifier);
			assertNotNull(prov.savedsugarcrmFields);
		}
	}

	/**
	 * Get providers (paged)
	 */
	@Test
	public void getProvidersPagingTest() {
		SugarCRMSearchResults<Provider> provs = cacheInstance.getProviders(null, 0, 0);
		assertNotNull(provs.items);
		assertTrue(!provs.items.isEmpty());

		for (Provider prov : provs.items) {
			System.out.println(prov.identifier);
			assertNotNull(prov.identifier);
			assertNotNull(prov.savedsugarcrmFields);
		}
	}

	/**
	 * Get Collection By Provider ID
	 */
	@Test
	public void getCollectionByProviderIDTest() {
		SugarCRMSearchResults<DataSet> collres = cacheInstance.getCollectionByProviderID("1");
		assertNotNull(collres.items);
		assertTrue(!collres.items.isEmpty());

		for (DataSet ds : collres.items) {
			assertNotNull(ds.identifier);
			assertNotNull(ds.edmDatasetName);
			assertNotNull(ds.status);
			assertNotNull(ds.provIdentifier);
			assertNotNull(ds.savedsugarcrmFields);
		}
	}

	/**
	 * Get Collection By ID
	 */
	@Test
	public void getCollectionByIDTest() {
		SugarCRMSearchResults<DataSet> collres = cacheInstance.getCollectionByID("91612");
		assertNotNull(collres.items);
		assertTrue(!collres.items.isEmpty());
		DataSet ds = collres.items.get(0);
		assertNotNull(ds);
		assertNotNull(ds.identifier);
		assertNotNull(ds.edmDatasetName);
		assertNotNull(ds.status);
		assertNotNull(ds.provIdentifier);
		assertNotNull(ds.savedsugarcrmFields);
	}

	/**
	 * Collection Polling method
	 * 
	 * @throws JIXBQueryResultException
	 */
	@Test
	public void collectionPollingTest() throws JIXBQueryResultException {
		importerInstance.pollCollections();
	}

	/**
	 * Provider polling method
	 * 
	 * @throws JIXBQueryResultException
	 */
	@Test
	public void providerPollingTest() throws JIXBQueryResultException {
		importerInstance.pollProviders();
	}
}

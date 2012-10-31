package eu.europeana.api2.web.controller.v1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import eu.europeana.api2.web.model.json.Api1SearchResults;
import eu.europeana.api2.web.model.json.ApiError;
import eu.europeana.api2.web.model.json.api1.BriefDoc;
import eu.europeana.corelib.db.service.ApiKeyService;
import eu.europeana.corelib.db.service.UserService;
import eu.europeana.corelib.definitions.solr.beans.ApiBean;
import eu.europeana.corelib.definitions.solr.beans.BriefBean;
import eu.europeana.corelib.definitions.solr.beans.IdBean;
import eu.europeana.corelib.definitions.solr.model.Query;
import eu.europeana.corelib.solr.exceptions.SolrTypeException;
import eu.europeana.corelib.solr.model.ResultSet;
import eu.europeana.corelib.solr.service.SearchService;

@Controller
public class SearchControllerV1 {

	private final Logger log = Logger.getLogger(getClass().getName());

	@Resource(name="corelib_db_userService") private UserService userService;

	@Resource private SearchService searchService;

	@Resource private ApiKeyService apiService;

	@Value("#{europeanaProperties['portal.name']}")
	private String portalName;

	@Value("#{europeanaProperties['portal.server']}")
	private String portalServer;

	private String path;

	@RequestMapping(value = {"/opensearch.json", "/v1/search.json"}, method=RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ModelAndView search2Json(
		@RequestParam(value = "wskey", required = true) String wskey,
		@RequestParam(value = "query", required = true) String q,
		@RequestParam(value = "qf", required = false) String[] refinements,
		@RequestParam(value = "profile", required = false, defaultValue="standard") String profile,
		@RequestParam(value = "start", required = false, defaultValue="1") int start,
		@RequestParam(value = "rows", required = false, defaultValue="12") int rows,
		@RequestParam(value = "sort", required = false) String sort,
		HttpServletRequest request
			) throws Exception {

		path = request.getContextPath();

		Map<String, Object> model = new HashMap<String, Object>();
		Api1Utils utils = new Api1Utils();

		boolean hasResult = false;
		if (!hasResult && StringUtils.isBlank(wskey)) {
			model.put("json", utils.toJson(new ApiError(wskey, "search.json", "No API authorisation key.")));
			hasResult = true;
		}

		if (!hasResult && (userService.findByApiKey(wskey) == null && apiService.findByID(wskey) == null)) {
			model.put("json", utils.toJson(new ApiError(wskey, "search.json", "Unregistered user")));
			hasResult = true;
		}

		if (!hasResult) {
			log.info("opensearch.json");
			Query query = new Query(q).setRefinements(refinements).setPageSize(rows).setStart(start - 1);
			Class<? extends IdBean> clazz = ApiBean.class;
			if (StringUtils.containsIgnoreCase(profile, "minimal")) {
				clazz = BriefBean.class;
			}
			try {
				log.info("->Api1SearchResults");
				log.info("profile? " + (profile == null));
				log.info("query? " + (query == null));
				log.info("clazz? " + (clazz == null));
				Api1SearchResults<Map<String, Object>> response = createResultsForApi1(wskey, profile, query, clazz);
				if (response != null) {
					log.info("got response " + response.items.size());
					model.put("json", utils.toJson(response));
				}
				model.put("result", response);
			} catch (SolrTypeException e) {
				logException(e);
			} catch (Exception e) {
				logException(e);
			}
		}

		ModelAndView page = new ModelAndView("search", model);
		return page;
	}

	private <T extends IdBean> Api1SearchResults<Map<String, Object>> createResultsForApi1(String wskey, String profile, Query q, 
			Class<T> clazz) 
			throws SolrTypeException {
		log.info("createResultsForApi1");
		Api1SearchResults<Map<String, Object>> response = new Api1SearchResults<Map<String, Object>>(wskey, "search.json");
		log.info("new Api1SearchResults");
		ResultSet<T> resultSet = searchService.search(clazz, q);
		log.info("searchService.search");
		response.totalResults = resultSet.getResultSize();
		response.itemsCount = resultSet.getResults().size();

		BriefDoc.setPortalServer(portalServer);
		BriefDoc.setPortalName(portalName);
		BriefDoc.setPath(path);
		List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
		for (Object o : resultSet.getResults()) {
			BriefDoc doc = new BriefDoc((ApiBean)o);
			doc.setWskey(wskey);
			items.add(doc.asMap());
		}
		log.info("new BriefDoc");
		response.items = items;
		log.info("response: " + response);
		return response;
	}

	private void logException(Exception e) {
		StringBuilder sb = new StringBuilder(e.getClass().getName());
		sb.append(": ").append(e.getMessage()).append("\n");
		StackTraceElement[] trace = e.getStackTrace();
		for (StackTraceElement el : trace) {
			sb.append(String.format("%s:%d %s()\n", el.getClassName(), el.getLineNumber(), el.getMethodName()));
		}
		log.severe(sb.toString());
	}
}

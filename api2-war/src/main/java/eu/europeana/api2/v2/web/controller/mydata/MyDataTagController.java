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

package eu.europeana.api2.v2.web.controller.mydata;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import eu.europeana.api2.v2.web.swagger.SwaggerIgnore;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import eu.europeana.api2.utils.JsonUtils;
import eu.europeana.api2.v2.model.json.ModificationConfirmation;
import eu.europeana.api2.v2.model.json.UserResults;
import eu.europeana.api2.v2.model.json.user.Tag;
import eu.europeana.api2.v2.web.controller.abstracts.AbstractUserController;
import eu.europeana.api2.v2.web.swagger.SwaggerSelect;
import eu.europeana.corelib.db.entity.relational.custom.TagCloudItem;
import eu.europeana.corelib.db.exception.DatabaseException;
import eu.europeana.corelib.definitions.db.entity.relational.SocialTag;
import eu.europeana.corelib.definitions.db.entity.relational.User;

/**
 * @author Willem-Jan Boogerd <www.eledge.net/contact>
 */
@Controller
@Api(value = "my_europeana", description = " ")
@SwaggerSelect
public class MyDataTagController extends AbstractUserController {

	/**
	 * @param europeanaId
	 * @param tag
	 * @param callback
     * @param principal
	 * @return the JSON response
	 */
	@SwaggerIgnore
	@RequestMapping(value = "/v2/mydata/tag.json", params = "!action", produces = MediaType.APPLICATION_JSON_VALUE)
	public ModelAndView defaultAction(
			@RequestParam(value = "europeanaid", required = false) String europeanaId,
			@RequestParam(value = "tag", required = false) String tag,
			@RequestParam(value = "callback", required = false) String callback,
			Principal principal) {
		return list(europeanaId, tag, callback, principal);
	}

	/**
	 * @param europeanaId
	 * @param tagFilter
	 * @param callback
	 * @param principal
	 * @return the JSON response
	 */
	@ApiOperation(value = "lets the user list their data tags", nickname = "listMyDataTags")
	@RequestMapping(value = "/v2/mydata/tag.json", params = "action=LIST", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
	public ModelAndView list(
			@RequestParam(value = "europeanaid", required = false) String europeanaId,
			@RequestParam(value = "tag", required = false) String tagFilter,
			@RequestParam(value = "callback", required = false) String callback,
			Principal principal) {
		UserResults<Tag> response = new UserResults<>(principal.getName(), "/v2/mydata/tag.json");
		try {
			User user = getUserByApiId(principal.getName());
			if (user != null) {
				response.items = new ArrayList<>();
				response.username = user.getUserName();
				List<SocialTag> tags;
				if (StringUtils.isNotBlank(tagFilter)) {
					tags = userService.findSocialTagsByTag(user.getId(), tagFilter);
				} else if (StringUtils.isNotBlank(europeanaId)) {
					tags = userService.findSocialTagsByEuropeanaId(user.getId(), europeanaId);
				} else {
					tags = new ArrayList<>(user.getSocialTags());
				}
				response.itemsCount = (long) tags.size();
				for (SocialTag item : tags) {
					Tag tag = new Tag();
					copyUserObjectData(response.apikey, tag, item);
					tag.tag = item.getTag();
					response.items.add(tag);
				}
				return JsonUtils.toJson(response, callback);
			} else {
				response.success = false;
				response.error = "Invalid credentials";
			}
		} catch (DatabaseException e) {
			response.success = false;
			response.error = e.getMessage();
		}
		return JsonUtils.toJson(response, callback);
	}


	/**
	 * @param callback
	 * @param principal
	 * @return the JSON response
	 */
	@ApiOperation(value = "shows the user's tag cloud", nickname = "showMyDataTagcloud")
	@RequestMapping(value = "/v2/mydata/tag.json", params = "action=TAGCLOUD", produces = MediaType.APPLICATION_JSON_VALUE
			, method = RequestMethod.GET)
	public ModelAndView listDistinct(
			@RequestParam(value = "callback", required = false) String callback,
			Principal principal) {
		UserResults<TagCloudItem> response = new UserResults<>(principal.getName(), "/v2/mydata/tag.json?action=TAGCLOUD");
			User user = getUserByApiId(principal.getName());
			if (user != null) {
				try {
					response.items = userService.createSocialTagCloud(user.getId());
					response.itemsCount = (long) response.items.size();
					response.success = true;
				} catch (DatabaseException e) {
					response.success = false;
					response.error = e.getMessage();
				}
				response.username = user.getUserName();
			} else {
				response.success = false;
				response.error = "Invalid credentials";
			}
		return JsonUtils.toJson(response, callback);
	}


	/**
	 * @param europeanaId
	 * @param tag
	 * @param callback
	 * @param principal
	 * @return the JSON response
	 */
	@ApiOperation(value = "lets the user create a new data tag", nickname = "createMyDataTag")
	@RequestMapping(value = "/v2/mydata/tag.json", produces = MediaType.APPLICATION_JSON_VALUE, method = {
			RequestMethod.POST, RequestMethod.PUT })
	public ModelAndView createRest(
			@RequestParam(value = "europeanaid", required = true) String europeanaId,
			@RequestParam(value = "tag", required = true) String tag,
			@RequestParam(value = "callback", required = false) String callback,
			Principal principal) {
		return create(europeanaId, tag, callback, principal);
	}

	/**
	 * @param europeanaId
	 * @param tag
	 * @param callback
	 * @param principal
	 * @return the JSON response
	 */
	@SwaggerIgnore
	@RequestMapping(value = "/v2/mydata/tag.json", params = "action=CREATE", produces = MediaType.APPLICATION_JSON_VALUE)
	public ModelAndView create(
			@RequestParam(value = "europeanaid", required = true) String europeanaId,
			@RequestParam(value = "tag", required = true) String tag,
			@RequestParam(value = "callback", required = false) String callback, 
			Principal principal) {
		ModificationConfirmation response = new ModificationConfirmation(principal.getName(), "/v2/mydata/tag.json?action=CREATE");
		User user = getUserByApiId(principal.getName());
		if (user != null) {
			try {
						userService.createSocialTag(user.getId(), europeanaId, tag);
						response.success = true;
					} catch (DatabaseException e) {
						response.success = false;
						response.error = e.getMessage();
					}
				} else {
					response.success = false;
					response.error = "Invalid credentials";
				}
		return JsonUtils.toJson(response, callback);
	}

	/**
	 * @param tagId
	 * @param tag
	 * @param europeanaId
	 * @param callback
	 * @param principal
	 * @return the JSON response
	 */
	@ApiOperation(value = "lets the user delete a data tag", nickname = "deleteMyDataTag")
	@RequestMapping(value = "/v2/mydata/tag.json", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.DELETE)
	public ModelAndView deleteRest(
			@RequestParam(value = "tagid", required = false) Long tagId,
			@RequestParam(value = "tag", required = false) String tag,
			@RequestParam(value = "europeanaid", required = false) String europeanaId,
			@RequestParam(value = "callback", required = false) String callback, 
			Principal principal) {
		return delete(tagId, tag, europeanaId, callback, principal);
	}

	/**
	 * @param tagId
	 * @param tag
	 * @param europeanaId
	 * @param callback
	 * @param principal
	 * @return the JSON response
	 */
	@SwaggerIgnore
	@RequestMapping(value = "/v2/mydata/tag.json", params = "action=DELETE", produces = MediaType.APPLICATION_JSON_VALUE)
	public ModelAndView delete(
			@RequestParam(value = "tagid", required = false) Long tagId,
			@RequestParam(value = "tag", required = false) String tag,
			@RequestParam(value = "europeanaid", required = false) String europeanaId,
			@RequestParam(value = "callback", required = false) String callback,
			Principal principal) {
		ModificationConfirmation response = new ModificationConfirmation(principal.getName(), "/v2/mydata/tag.json?action=DELETE");
		try {
			User user = getUserByApiId(principal.getName());
			if (user != null) {
				if (tagId != null) {
					userService.removeSocialTag(user.getId(), tagId);
				} else {
					if (StringUtils.isNotBlank(europeanaId) || StringUtils.isNotBlank(tag)) {
						userService.removeSocialTag(user.getId(), europeanaId, tag);
					}
				}
				response.success = true;
			}
		} catch (DatabaseException e) {
			response.success = false;
			response.error = e.getMessage();
		}
		return JsonUtils.toJson(response, callback);
	}
	
	
}

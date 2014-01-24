package eu.europeana.api2.web.controller;

import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.provider.AuthorizationRequest;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for retrieving the model for and displaying the confirmation page
 * for access to a protected resource.
 */
@Controller
@SessionAttributes(types = AuthorizationRequest.class)
public class AccessConfirmationController {

	private ClientDetailsService clientDetailsService;

	@RequestMapping("/oauth/confirm_access")
	public ModelAndView getAccessConfirmation(
			@ModelAttribute AuthorizationRequest clientAuth) throws Exception {
		ClientDetails client = clientDetailsService
				.loadClientByClientId(clientAuth.getClientId());
		TreeMap<String, Object> model = new TreeMap<String, Object>();
		model.put("auth_request", clientAuth);
		model.put("client", client);
		return new ModelAndView("user/authorize", model);
	}
//	
//	@RequestMapping("/login")
//	public String loginForm() {
//		return "user/login";
//	}

	@RequestMapping(value="/login", params="form=user")
	public String loginUserForm() {
		return "user/login";
	}


	@RequestMapping(value="/login", params="form=myData")
	public String loginMyDataForm() {
		return "mydata/login";
	}
	
	@Autowired
	public void setClientDetailsService(
			ClientDetailsService clientDetailsService) {
		this.clientDetailsService = clientDetailsService;
	}
}

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

package eu.europeana.api2.web.controller;

import org.springframework.beans.TypeMismatchException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import eu.europeana.api2.model.json.ApiError;
import eu.europeana.corelib.db.exception.LimitReachedException;

/**
 * @author Willem-Jan Boogerd <www.eledge.net/contact>
 */
@Controller
public class ExceptionController {

	@ExceptionHandler({TypeMismatchException.class, MissingServletRequestParameterException.class})
	@ResponseBody
	public ApiError handleMismatchException(Exception ex) {
		return new ApiError(null, "Invalid argument(s): " + ex.toString());
	}

	@ExceptionHandler(LimitReachedException.class)
	@ResponseBody
	public ApiError handleLimitReachedException() {
		return new ApiError(null, "API Usage Limit Reached.");
	}
}

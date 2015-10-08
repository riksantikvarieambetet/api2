<%@ page session="false" language="java" contentType="charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<%--
  ~ Copyright 2007-2015 The Europeana Foundation
  ~
  ~ Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved
  ~ by the European Commission;
  ~ You may not use this work except in compliance with the Licence.
  ~
  ~ You may obtain a copy of the Licence at:
  ~ http://joinup.ec.europa.eu/software/page/eupl
  ~
  ~ Unless required by applicable law or agreed to in writing, software distributed under
  ~ the Licence is distributed on an "AS IS" basis, without warranties or conditions of
  ~ any kind, either express or implied.
  ~ See the Licence for the specific language governing permissions and limitations under
  ~ the Licence.
  --%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
	<title>Europeana API Login</title>
</head>

<body>

	<h1>EUROPEANA.EU API</h1>

	<div id="content">
		<c:if test="${not empty param.authentication_error}">
			<h1>Woops!</h1>

			<p class="error">Your login attempt was not successful.</p>
		</c:if>
		<c:if test="${not empty param.authorization_error}">
			<h1>Woops!</h1>

			<p class="error">You are not permitted to access that resource.</p>
		</c:if>

		<h2>Login</h2>

		<p>
			This page is only for testing, please use a POST http request to <strong>/api/login.do</strong>
			with the following attributes:<br />
			<li><strong>j_username</strong>: containing your public apikey</li>
			<li><strong>j_password</strong>: containing your private key</li>
		</p>
		<form id="loginForm" name="loginForm"
			action="<c:url value="/login.do"/>" method="post">
			<p>
				<label>Public api key: <input type='text' name='j_username'/></label>
			</p>
			<p>
				<label>Secret api key: <input type="text" name='j_password' /></label>
			</p>

			<p>
				<input name="login" value="Login" type="submit" />
			</p>
		</form>
	</div>

</body>
</html>

/*
 * Copyright 2011 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.overlord.sramp.server.atom.services;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.jboss.resteasy.util.GenericType;
import org.overlord.sramp.atom.MediaType;
import org.overlord.sramp.atom.err.SrampAtomException;
import org.overlord.sramp.common.Sramp;
import org.overlord.sramp.server.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implementation of the S-RAMP query features defined in the Atom Binding document.
 */
@Path("/s-ramp")
public class QueryResource extends AbstractFeedResource {

	private static Logger logger = LoggerFactory.getLogger(QueryResource.class);

	private final Sramp sramp = new Sramp();

	/**
	 * Constructor.
	 */
	public QueryResource() {
	}

	/**
	 * Do an s-ramp query from a GET style request.
	 * @param uri
	 * @param query
	 * @param startPage
	 * @param startIndex
	 * @param count
	 * @param orderBy
	 * @param asc
	 * @throws SrampAtomException
	 */
	@GET
	@Produces(MediaType.APPLICATION_ATOM_XML_FEED)
	public Feed queryFromGet(
			@Context HttpServletRequest request,
			@QueryParam("query") String query,
			@QueryParam("startPage") Integer startPage,
			@QueryParam("startIndex") Integer startIndex,
			@QueryParam("count") Integer count,
			@QueryParam("orderBy") String orderBy,
			@QueryParam("ascending") Boolean asc,
			@QueryParam("propertyName") Set<String> propNames) throws SrampAtomException {
		try {
			String baseUrl = sramp.getBaseUrl(request.getRequestURL().toString());
			if (startIndex == null && startPage != null) {
				int c = count != null ? count.intValue() : 100;
				startIndex = (startPage.intValue() - 1) * c;
			}
			return query(query, startIndex, count, orderBy, asc, propNames, baseUrl);
		} catch (Throwable e) {
			logError(logger, Messages.i18n.format("ERROR_EXECUTING_QUERY", query), e); //$NON-NLS-1$
			throw new SrampAtomException(e);
		}
	}

	/**
	 * Handles clients that POST the query to the /s-ramp endpoint.
	 * @param input the multipart form data
	 * @throws SrampAtomException
	 */
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_ATOM_XML_FEED)
	public Feed queryFromPost(@Context HttpServletRequest request, MultipartFormDataInput input) throws SrampAtomException {
		String query = null;
		try {
			String baseUrl = sramp.getBaseUrl(request.getRequestURL().toString());
			query = input.getFormDataPart("query", new GenericType<String>() { }); //$NON-NLS-1$
			Integer startPage = input.getFormDataPart("startPage", new GenericType<Integer>() { }); //$NON-NLS-1$
			Integer startIndex = input.getFormDataPart("startIndex", new GenericType<Integer>() { }); //$NON-NLS-1$
			Integer count = input.getFormDataPart("count", new GenericType<Integer>() { }); //$NON-NLS-1$
			String orderBy = input.getFormDataPart("orderBy", new GenericType<String>() { }); //$NON-NLS-1$
			Boolean asc = input.getFormDataPart("ascending", new GenericType<Boolean>() { }); //$NON-NLS-1$
            Set<String> propNames = new HashSet<String>();
			List<InputPart> list = input.getFormDataMap().get("propertyName"); //$NON-NLS-1$
			if (list != null) {
    			for (InputPart inputPart : list) {
    			    propNames.add(inputPart.getBodyAsString());
                }
			}

			if (startIndex == null && startPage != null) {
				int c = count != null ? count.intValue() : 100;
				startIndex = (startPage.intValue() - 1) * c;
			}
			return query(query, startIndex, count, orderBy, asc, propNames, baseUrl);
		} catch (SrampAtomException e) {
			throw e;
		} catch (Throwable e) {
			logError(logger, Messages.i18n.format("ERROR_EXECUTING_QUERY", query), e); //$NON-NLS-1$
			throw new SrampAtomException(e);
		}
	}

	/**
	 * Common method that performs the query and returns the atom {@link Feed}.
	 * @param query the x-path formatted s-ramp query
	 * @param startIndex index of the first item in the result that should be returned
	 * @param count the number of items to return
	 * @param orderBy the property to sort the results by
	 * @param ascending the sort direction
	 * @param propNames the set of s-ramp property names - the extra properties that the query should return as part of the {@link Feed}
	 * @return an Atom {@link Feed}
	 * @throws SrampAtomException
	 */
	protected Feed query(String query, Integer startIndex, Integer count, String orderBy, Boolean ascending,
			Set<String> propNames, String baseUrl) throws SrampAtomException {
		if (query == null)
			throw new SrampAtomException(Messages.i18n.format("MISSING_QUERY_PARAM")); //$NON-NLS-1$

		// Add on the "/s-ramp/" if it's missing
		String xpath = query;
		if (!xpath.startsWith("/s-ramp")) { //$NON-NLS-1$
			if (query.startsWith("/")) //$NON-NLS-1$
				xpath = "/s-ramp" + query; //$NON-NLS-1$
			else
				xpath = "/s-ramp/" + query; //$NON-NLS-1$
		}

		return createArtifactFeed(xpath, startIndex, count, orderBy, ascending, propNames, baseUrl);
	}
}

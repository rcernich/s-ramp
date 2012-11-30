/*
 * Copyright 2012 JBoss Inc
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
package org.overlord.sramp.atom.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.namespace.QName;

import org.apache.commons.io.IOUtils;
import org.jboss.resteasy.plugins.providers.atom.Entry;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartConstants;
import org.jboss.resteasy.plugins.providers.multipart.MultipartRelatedInput;
import org.jboss.resteasy.util.GenericType;
import org.overlord.sramp.ArtifactType;
import org.overlord.sramp.ArtifactTypeEnum;
import org.overlord.sramp.MimeTypes;
import org.overlord.sramp.Sramp;
import org.overlord.sramp.SrampConstants;
import org.overlord.sramp.atom.MediaType;
import org.overlord.sramp.atom.SrampAtomUtils;
import org.overlord.sramp.atom.err.SrampAtomException;
import org.overlord.sramp.atom.visitors.ArtifactContentTypeVisitor;
import org.overlord.sramp.atom.visitors.ArtifactToFullAtomEntryVisitor;
import org.overlord.sramp.repository.PersistenceFactory;
import org.overlord.sramp.repository.PersistenceManager;
import org.overlord.sramp.visitors.ArtifactVisitorHelper;
import org.s_ramp.xmlns._2010.s_ramp.BaseArtifactType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JAX-RS resource that handles artifact specific tasks, including:
 *
 * <ul>
 * <li>Add an artifact (upload)</li>
 * <li>Get an artifact (full Atom {@link Entry})</li>
 * <li>Get artifact content (binary content)</li>
 * <li>Update artifact meta data</li>
 * <li>Update artifact content</li>
 * <li>Delete an artifact</li>
 * </ul>
 *
 * @author eric.wittmann@redhat.com
 */
@Path("/s-ramp")
public class ArtifactResource {

	private static Logger logger = LoggerFactory.getLogger(BatchResource.class);

	// Sadly, date formats are not thread safe.
	private static final ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
		@Override
		protected SimpleDateFormat initialValue() {
			return new SimpleDateFormat(SrampConstants.DATE_FORMAT);
		}
	};

	private final Sramp sramp = new Sramp();

	/**
	 * Constructor.
	 */
	public ArtifactResource() {
	}

	/**
	 * S-RAMP atom POST to upload an artifact to the repository. The artifact content should be POSTed raw.
	 *
	 * @param fileName
	 * @param model
	 * @param type
	 * @param content
	 * @throws SrampAtomException
	 */
	@POST
	@Path("{model}/{type}")
	@Produces(MediaType.APPLICATION_ATOM_XML_ENTRY)
	public Entry create(@Context HttpServletRequest request, @HeaderParam("Content-Type") String contentType,
	        @HeaderParam("Slug") String fileName, @PathParam("model") String model,
	        @PathParam("type") String type, InputStream is) throws SrampAtomException {
		try {
			String baseUrl = sramp.getBaseUrl(request.getRequestURL().toString());
			ArtifactType artifactType = ArtifactType.valueOf(model, type);
			if (artifactType.getArtifactType().isDerived()) {
				throw new Exception("Failed to create artifact because '" + artifactType.getArtifactType()
				        + "' is a derived type.");
			}
			// Figure out the mime type (from the http header, filename, or default by artifact type)
			String mimeType = MimeTypes.determineMimeType(contentType, fileName, artifactType);
			artifactType.setMimeType(mimeType);

			// Pick a reasonable file name if Slug is not present
			if (fileName == null) {
				if (artifactType.getArtifactType() == ArtifactTypeEnum.Document) {
					fileName = "newartifact.bin";
				} else if (artifactType.getArtifactType() == ArtifactTypeEnum.XmlDocument) {
					fileName = "newartifact.xml";
				} else {
					fileName = "newartifact." + artifactType.getArtifactType().getModel();
				}
			}

			PersistenceManager persistenceManager = PersistenceFactory.newInstance();
			// store the content
			BaseArtifactType baseArtifactType = artifactType.newArtifactInstance();
			baseArtifactType.setName(fileName);
			BaseArtifactType artifact = persistenceManager.persistArtifact(baseArtifactType, is);

			// return the entry containing the s-ramp artifact
			ArtifactToFullAtomEntryVisitor visitor = new ArtifactToFullAtomEntryVisitor(baseUrl);
			ArtifactVisitorHelper.visitArtifact(visitor, artifact);
			return visitor.getAtomEntry();
		} catch (Exception e) {
			logger.error("Error creating an artifact.", e);
			throw new SrampAtomException(e);
		} finally {
			IOUtils.closeQuietly(is);
		}
	}

	/**
	 * Handles multi-part creates. In S-RAMP, an HTTP multi-part request can be POST'd to the endpoint, which
	 * allows Atom Entry formatted meta-data to be included in the same request as the artifact content.
	 *
	 * @param contentType
	 * @param model
	 * @param type
	 * @param input
	 * @return the newly created artifact as an Atom {@link Entry}
	 * @throws SrampAtomException
	 */
	@POST
	@Path("{model}/{type}")
	@Consumes(MultipartConstants.MULTIPART_RELATED)
	@Produces(MediaType.APPLICATION_ATOM_XML_ENTRY)
	public Entry createMultiPart(@Context HttpServletRequest request,
	        @HeaderParam("Content-Type") String contentType, @PathParam("model") String model,
	        @PathParam("type") String type, MultipartRelatedInput input) throws SrampAtomException {
		InputStream contentStream = null;
		try {
			String baseUrl = sramp.getBaseUrl(request.getRequestURL().toString());
			ArtifactType artifactType = ArtifactType.valueOf(model, type);
			if (artifactType.getArtifactType().isDerived()) {
				throw new Exception("Failed to create artifact because '" + artifactType.getArtifactType()
				        + "' is a derived type.");
			}

			List<InputPart> list = input.getParts();
			// Expecting 2 parts
			if (list.size() != 2) {
				throw new SrampAtomException("Invalid multi-part POST - expected two parts but got "
				        + list.size());
			}
			InputPart firstPart = list.get(0);
			InputPart secondpart = list.get(1);

			// Getting the S-RAMP Artifact
			Entry atomEntry = firstPart.getBody(new GenericType<Entry>() {
			});
			BaseArtifactType artifactMetaData = SrampAtomUtils.unwrapSrampArtifact(artifactType, atomEntry);
			ArtifactType metaDataType = ArtifactType.valueOf(artifactMetaData);
			if (metaDataType.getArtifactType() != artifactType.getArtifactType()) {
				String errorMsg = String.format(
				        "Invalid multi-part POST - attempted to POST a '%1$s' to the '%2$s' endpoint.",
				        metaDataType.getArtifactType().getType(), artifactType.getArtifactType().getType());
				throw new SrampAtomException(errorMsg);
			}
			String fileName = null;
			if (artifactMetaData.getName() != null)
				fileName = artifactMetaData.getName();
			String mimeType = MimeTypes.determineMimeType(contentType, fileName, artifactType);
			artifactType.setMimeType(mimeType);

			// Processing the content itself first
			contentStream = secondpart.getBody(new GenericType<InputStream>() {
			});
			PersistenceManager persistenceManager = PersistenceFactory.newInstance();
			// store the content
			BaseArtifactType artifactRval = persistenceManager.persistArtifact(artifactMetaData,
			        contentStream);

			// Convert to a full Atom Entry and return it
			ArtifactToFullAtomEntryVisitor visitor = new ArtifactToFullAtomEntryVisitor(baseUrl);
			ArtifactVisitorHelper.visitArtifact(visitor, artifactRval);
			return visitor.getAtomEntry();
		} catch (Exception e) {
			logger.error("Error creating an artifact.", e);
			throw new SrampAtomException(e);
		} finally {
			IOUtils.closeQuietly(contentStream);
		}
	}

	/**
	 * Called to update the meta data for an artifact. Note that this does *not* update the content of the
	 * artifact, just the meta data.
	 *
	 * @param model
	 * @param type
	 * @param uuid
	 * @param atomEntry
	 * @throws SrampAtomException
	 */
	@PUT
	@Path("{model}/{type}/{uuid}")
	@Consumes(MediaType.APPLICATION_ATOM_XML_ENTRY)
	public void updateMetaData(@PathParam("model") String model, @PathParam("type") String type,
	        @PathParam("uuid") String uuid, Entry atomEntry) throws SrampAtomException {
		try {
			ArtifactType artifactType = ArtifactType.valueOf(model, type);
			BaseArtifactType artifact = SrampAtomUtils.unwrapSrampArtifact(artifactType, atomEntry);
			PersistenceManager persistenceManager = PersistenceFactory.newInstance();
			persistenceManager.updateArtifact(artifact, artifactType);
		} catch (Throwable e) {
			logger.error("Error updating artifact meta data for: " + uuid, e);
			throw new SrampAtomException(e);
		}
	}

	/**
	 * S-RAMP atom PUT to upload a new version of the artifact into the repository.
	 *
	 * @param model
	 * @param type
	 * @param uuid
	 * @param content
	 * @throws SrampAtomException
	 */
	@PUT
	@Path("{model}/{type}/{uuid}/media")
	public void updateContent(@HeaderParam("Content-Type") String contentType,
	        @HeaderParam("Slug") String fileName, @PathParam("model") String model,
	        @PathParam("type") String type, @PathParam("uuid") String uuid, InputStream content)
	        throws SrampAtomException {
		ArtifactType artifactType = ArtifactType.valueOf(model, type);
		if (artifactType.getArtifactType().isDerived()) {
			throw new SrampAtomException("Failed to create artifact because '"
			        + artifactType.getArtifactType() + "' is a derived type.");
		}
		String mimeType = MimeTypes.determineMimeType(contentType, fileName, artifactType);
		artifactType.setMimeType(mimeType);
		// TODO we need to update the S-RAMP metadata too (new updateDate, size, etc)?
		InputStream is = content;
		try {
			PersistenceManager persistenceManager = PersistenceFactory.newInstance();
			// store the content
			persistenceManager.updateArtifactContent(uuid, artifactType, is);
		} catch (Exception e) {
			logger.error("Error updating artifact content for: " + uuid, e);
			throw new SrampAtomException(e);
		} finally {
			IOUtils.closeQuietly(is);
		}
	}

	/**
	 * Called to get the meta data for an s-ramp artifact. This will return an Atom {@link Entry} with the
	 * full information about the artifact.
	 *
	 * @param model
	 * @param type
	 * @param uuid
	 * @throws SrampAtomException
	 */
	@GET
	@Path("{model}/{type}/{uuid}")
	@Produces(MediaType.APPLICATION_ATOM_XML_ENTRY)
	public Entry getMetaData(@Context HttpServletRequest request, @PathParam("model") String model,
	        @PathParam("type") String type, @PathParam("uuid") String uuid) throws SrampAtomException {
		try {
			String baseUrl = sramp.getBaseUrl(request.getRequestURL().toString());
			ArtifactType artifactType = ArtifactType.valueOf(model, type);
			PersistenceManager persistenceManager = PersistenceFactory.newInstance();

			// Get the artifact by UUID
			BaseArtifactType artifact = persistenceManager.getArtifact(uuid, artifactType);
			if (artifact == null)
				throw new Exception("Artifact not found.");

			// Return the entry containing the s-ramp artifact
			ArtifactToFullAtomEntryVisitor visitor = new ArtifactToFullAtomEntryVisitor(baseUrl);
			ArtifactVisitorHelper.visitArtifact(visitor, artifact);
			return visitor.getAtomEntry();
		} catch (Throwable e) {
			logger.error("Error getting artifact meta data for: " + uuid, e);
			throw new SrampAtomException(e);
		}
	}

	/**
	 * Returns the content of an artifact in the s-ramp repository.
	 *
	 * @param model
	 * @param type
	 * @param uuid
	 * @throws SrampAtomException
	 */
	@GET
	@Path("{model}/{type}/{uuid}/media")
	public Response getContent(@PathParam("model") String model, @PathParam("type") String type,
	        @PathParam("uuid") String uuid) throws SrampAtomException {
		try {
			ArtifactType artifactType = ArtifactType.valueOf(model, type);
			PersistenceManager persistenceManager = PersistenceFactory.newInstance();
			BaseArtifactType baseArtifact = persistenceManager.getArtifact(uuid, artifactType);
			ArtifactContentTypeVisitor ctVizzy = new ArtifactContentTypeVisitor();
			ArtifactVisitorHelper.visitArtifact(ctVizzy, baseArtifact);
			javax.ws.rs.core.MediaType mediaType = ctVizzy.getContentType();
			artifactType.setMimeType(mediaType.toString());
			final InputStream artifactContent = persistenceManager.getArtifactContent(uuid, artifactType);
			Object output = new StreamingOutput() {
				@Override
				public void write(OutputStream output) throws IOException, WebApplicationException {
					try {
						IOUtils.copy(artifactContent, output);
					} finally {
						IOUtils.closeQuietly(artifactContent);
					}
				}
			};
			String lastModifiedDate = dateFormat.get().format(
			        baseArtifact.getLastModifiedTimestamp().toGregorianCalendar().getTime());
			return Response
			        .ok(output, artifactType.getMimeType())
			        .header("Content-Disposition", "attachment; filename=" + baseArtifact.getName())
			        .header("Content-Length",
			                baseArtifact.getOtherAttributes().get(
			                        new QName(SrampConstants.SRAMP_CONTENT_SIZE)))
			        .header("Last-Modified", lastModifiedDate).build();
		} catch (Throwable e) {
			logger.error("Error getting artifact content for: " + uuid, e);
			throw new SrampAtomException(e);
		}
	}

	/**
	 * Called to delete an s-ramp artifact from the repository.
	 *
	 * @param model
	 * @param type
	 * @param uuid
	 * @throws SrampAtomException
	 */
	@DELETE
	@Path("{model}/{type}/{uuid}")
	public void delete(@PathParam("model") String model, @PathParam("type") String type,
	        @PathParam("uuid") String uuid) throws SrampAtomException {
		try {
			ArtifactType artifactType = ArtifactType.valueOf(model, type);
			PersistenceManager persistenceManager = PersistenceFactory.newInstance();

			// Delete the artifact by UUID
			persistenceManager.deleteArtifact(uuid, artifactType);
		} catch (Throwable e) {
			logger.error("Error deleting artifact: " + uuid, e);
			throw new SrampAtomException(e);
		}
	}

}

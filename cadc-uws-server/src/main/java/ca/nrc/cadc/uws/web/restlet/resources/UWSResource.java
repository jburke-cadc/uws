/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*                                       
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*                                       
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*                                       
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*                                       
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*                                       
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/


package ca.nrc.cadc.uws.web.restlet.resources;

import java.io.IOException;
import java.security.PrivilegedActionException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.restlet.Request;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.uws.server.JobManager;
import ca.nrc.cadc.uws.web.InlineContentHandler;
import ca.nrc.cadc.uws.web.restlet.RestletPrincipalExtractor;
import ca.nrc.cadc.uws.web.restlet.UWSAsyncApplication;
import ca.nrc.cadc.uws.web.restlet.WebRepresentationException;
import ca.nrc.cadc.uws.web.restlet.representation.JDOMRepresentation;
import ca.nrc.cadc.uws.web.restlet.validators.JobFormValidatorImpl;
import ca.nrc.cadc.uws.web.validators.FormValidator;


/**
 * Base XML Resource for UWS Resources.
 */
public abstract class UWSResource extends ServerResource
{
    private static final Logger LOGGER = Logger.getLogger(UWSResource.class);

    private static final String CERTIFICATE_REQUEST_ATTRIBUTE_NAME = "org.restlet.https.clientCertificates";

    private Subject subject;
    protected FormValidator formValidator;

    /**
     * Constructor.
     */
    protected UWSResource()
    {
        LOGGER.debug("CONSTRUCTOR: " + this.getClass().getName());
    }

    @Override
    protected void doInit()
    {
        super.doInit();

        // Create a subject for authentication
        final Request request = getRequest();
        this.subject = AuthenticationUtil.getSubject(new RestletPrincipalExtractor(request));
        LOGGER.debug(subject);
    }


    /**
     * Obtain the XML Representation of this Request.
     *
     * @return      The XML Representation, fully populated.
     */
    @Get
    public Representation represent()
    {
        try
        {
            Document document = new Document();
            buildXML(document);
            return new JDOMRepresentation(MediaType.TEXT_XML, document);
        }
        catch (final IOException e)
        {
            setExisting(false);
            LOGGER.error("Unable to create XML Document.");
            throw new WebRepresentationException(
                    "Unable to create XML Document.", e);
        }

    }

    /**
     * Generate the error Representation.
     *
     * @param errors Map of errors in the form.
     */
    protected void generateErrorRepresentation(final Map<String, String> errors)
    {
        final StringBuilder errorMessage = new StringBuilder(128);

        errorMessage.append("Errors found during Job Creation: \n");

        for (final Map.Entry<String, String> error : errors.entrySet())
        {
            errorMessage.append("\n");
            errorMessage.append(error.getKey());
            errorMessage.append(": ");
            errorMessage.append(error.getValue());
        }

        getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        getResponse().setEntity(
                new StringRepresentation(errorMessage.toString()));
    }

    /**
     * Generate the error Representation.
     *
     * @param status
     * @param errorMsg
     */
    protected void generateErrorRepresentation(final Status status, final String errorMsg)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(errorMsg);
        if (!errorMsg.endsWith("\n"))
            sb.append("\n");
        getResponse().setStatus(status);
        getResponse().setEntity(new StringRepresentation(sb.toString()));
    }
    
    /**
     * Generate the error Representation.
     *
     * @param t        Error in the form.
     * @return 
     */
    protected Representation generateRetryRepresentation(TransientException t)
    {
        final StringBuilder errorMessage = new StringBuilder(128);

        errorMessage.append("Service Busy");

        getResponse().setStatus(Status.SERVER_ERROR_SERVICE_UNAVAILABLE);
        
        Calendar retryTime = new GregorianCalendar();
        LOGGER.debug("After transient exception, setting retry-after to be "
                + t.getRetryDelay() + " seconds.");
        retryTime.add(Calendar.SECOND, t.getRetryDelay());
        getResponse().setRetryAfter(retryTime.getTime());
        
        return new StringRepresentation(errorMessage.toString());
    }

    /**
     * Validate the POST data.
     *
     * @param form        The form data to validate.
     * @return  True if the Form is fine for creation, False otherwise.
     */
    protected Map<String, String> validate(final Form form)
    {
        final FormValidator validator = new JobFormValidatorImpl(form);
        return validator.validate();
    }

    /**
     * Assemble the XML for this Resource's Representation into the given
     * Document.
     *
     * @param document The Document to build up.
     * @throws java.io.IOException If something went wrong or the XML cannot be
     *                             built.
     */
    protected abstract void buildXML(final Document document)
                throws IOException;

    /**
     * Build the host portion of any outgoing URL that will be intended for a
     * local call.  This is useful when building XML and wanting to call upon
     * a local Resource to build a portion of it.
     *
     * An example would look like: http://myhost/context
     *
     * @return      String Host part of a URI.
     */
    protected String getHostPart()
    {
        final StringBuilder elementURI = new StringBuilder(128);
        final Reference ref = getRequest().getResourceRef();

        elementURI.append(ref.getSchemeProtocol().getSchemeName());
        elementURI.append("://");
        elementURI.append(ref.getHostDomain());
        int p = ref.getHostPort();
        if (p != -1)
            elementURI.append(":").append(p);

        return elementURI.toString();
    }

    /**
     * Return the original path from the Request.
     *
     * @return String Request Path.
     */
    protected String getRequestPath()
    {
        String path = getRequest().getOriginalRef().getPath().trim();
        if (path.endsWith("/"))
        {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
    
    /**
     * Return the IP address of the original HTTP requester.
     * 
     * @return String of requester IP
     */
    protected String getRemoteIP()
    {
        return getRequest().getClientInfo().getAddress();
    }

    /**
     * Get a read-only subject object containing the principals found
     * in the HttpServletRequest.
     * 
     * @return the Subject with available Principals, or null if no
     * Principals were found.
     */
    protected Subject getSubject()
    {
        return subject;
    }

    /**
     * Obtain this Resource's Job Service.
     * @return  JobService instance, or null if none set.
     */
    protected JobManager getJobManager()
    {
        return (JobManager) getContextAttribute(UWSAsyncApplication.UWS_JOB_MANAGER);
    }
    
    /*
     *
     */
    protected InlineContentHandler getInlineContentHandler()
    {
        InlineContentHandler handler = null;
        Class c = (Class) getContextAttribute(UWSAsyncApplication.UWS_INLINE_CONTENT_HANDLER);
        if (c != null)
        {
            try
            {
                handler = (InlineContentHandler) c.newInstance();
                LOGGER.debug("inline content handler: " + handler.getClass().getName());
            }
            catch (Throwable t)
            {
                LOGGER.error("Unable to create inline content handler ", t);
            }
        }
        return handler;
    }

    protected Object getContextAttribute(final String key)
    {
        return getContext().getAttributes().get(key);
    }

    protected String getRequestAttribute(final String attributeName)
    {
        return (String) getRequestAttributes().get(attributeName);
    }    

    public FormValidator getFormValidator()
    {
        return formValidator;
    }

    public void setFormValidator(final FormValidator formValidator)
    {
        this.formValidator = formValidator;
    }
}

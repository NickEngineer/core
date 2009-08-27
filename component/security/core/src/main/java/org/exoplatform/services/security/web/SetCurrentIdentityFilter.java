/*
 * Copyright (C) 2009 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.services.security.web;

import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationRegistry;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.services.security.StateKey;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Created by The eXo Platform SAS .
 * 
 * @author <a href="mailto:gennady.azarenkov@exoplatform.com">Gennady
 *         Azarenkov</a>
 * @version $Id: SimpleSessionFactoryInitializedFilter.java 7163 2006-07-19
 *          07:30:39Z peterit $
 */
public class SetCurrentIdentityFilter implements Filter
{

   /**
    * Under this name can be set portal container name, as filter
    * <tt>init-param</tt> or application <tt>context-param</tt>. If both of
    * parameters not set then application context-name used as container name.
    */
   public static final String PORTAL_CONTAINER_NAME = "portalContainerName";

   /**
    * Logger.
    */
   private static Log log = ExoLogger.getLogger("core.security.SetCurrentIdentityFilter");

   /**
    * Portal Container name.
    */
   private String portalContainerName;

   /**
    * {@inheritDoc}
    */
   public void init(FilterConfig config) throws ServletException
   {
      // It is not possible to use application context name everywhere cause to
      // problem with access to resources (CSS). Try to find container name in
      // filter 'init-param' or in application 'context-param'. And only if both of
      // parameters are not specified then use application context name.

      // Check filter init-param first
      portalContainerName = config.getInitParameter(PORTAL_CONTAINER_NAME);

      // check application context-param
      if (portalContainerName == null)
         portalContainerName = config.getServletContext().getInitParameter(PORTAL_CONTAINER_NAME);

      // if nothing set then use application context name, 'display-name' in
      // web.xml
      if (portalContainerName == null)
         portalContainerName = config.getServletContext().getServletContextName();

      // save container name as attribute
      config.getServletContext().setAttribute(PORTAL_CONTAINER_NAME, portalContainerName);
   }

   /**
    * Set current {@link ConversationState}, if it is not registered yet then
    * create new one and register in {@link ConversationRegistry}. {@inheritDoc}
    */
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
      ServletException
   {

      HttpServletRequest httpRequest = (HttpServletRequest)request;
      ExoContainer container = ExoContainerContext.getContainerByName(portalContainerName);
      if (container == null)
      {
         if (log.isDebugEnabled())
         {
            log.debug("Container " + portalContainerName + " not found.");
         }

         container = ExoContainerContext.getTopContainer();
      }

      try
      {
         ExoContainerContext.setCurrentContainer(container);
         ConversationState state = getCurrentState(container, httpRequest);
         // NOTE may be set as null
         ConversationState.setCurrent(state);
         if (state != null && log.isDebugEnabled())
            log.debug(">>> Memberships " + state.getIdentity().getMemberships());
         chain.doFilter(request, response);
      }
      finally
      {
         try
         {
            ConversationState.setCurrent(null);
         }
         catch (Exception e)
         {
            log.warn("An error occured while cleaning the ThreadLocal", e);
         }
         try
         {
            ExoContainerContext.setCurrentContainer(null);
         }
         catch (Exception e)
         {
            log.warn("An error occured while cleaning the ThreadLocal", e);
         }
      }
   }

   /**
    * Gives the current state
    */
   private ConversationState getCurrentState(ExoContainer container, HttpServletRequest httpRequest)
   {
      ConversationRegistry conversationRegistry =
         (ConversationRegistry)container.getComponentInstanceOfType(ConversationRegistry.class);

      IdentityRegistry identityRegistry =
         (IdentityRegistry)container.getComponentInstanceOfType(IdentityRegistry.class);

      ConversationState state = null;
      String userId = httpRequest.getRemoteUser();

      // only if user authenticated, otherwise there is no reason to do anythings
      if (userId != null)
      {
         HttpSession httpSession = httpRequest.getSession();
         StateKey stateKey = new HttpSessionStateKey(httpSession);

         if (log.isDebugEnabled())
         {
            log.debug("Looking for Conversation State " + httpSession.getId());
         }

         state = conversationRegistry.getState(stateKey);

         if (state == null)
         {
            if (log.isDebugEnabled())
            {
               log.debug("Conversation State not found, try create new one.");
            }

            Identity identity = identityRegistry.getIdentity(userId);
            if (identity != null)
            {
               state = new ConversationState(identity);
               // keep subject as attribute in ConversationState
               state.setAttribute(ConversationState.SUBJECT, identity.getSubject());
            }
            else
               log.error("Not found identity in IdentityRegistry for user " + userId + ", check Login Module.");

            if (state != null)
            {
               conversationRegistry.register(stateKey, state);
               if (log.isDebugEnabled())
               {
                  log.debug("Register Conversation state " + httpSession.getId());
               }

            }
         }
      }
      return state;
   }

   /**
    * {@inheritDoc}
    */
   public void destroy()
   {
      // nothing to do.
   }

}

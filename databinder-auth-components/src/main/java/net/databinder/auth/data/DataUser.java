/*
 * Databinder: a simple bridge from Wicket to Hibernate
 * Copyright (C) 2006  Nathan Hamblen nathan@technically.us
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.databinder.auth.data;

import org.apache.wicket.authorization.strategies.role.Roles;

/**
 * Base user interface.
 * @author Nathan Hamblen
 */
public interface DataUser {
	/** @return true if user has any role matching those given */
	public boolean hasAnyRole(Roles roles);
	/** @return true if password is valid for this user. */
	public boolean checkPassword(String password);
	
	/**
	 * Sub-interface for user classes supporting cookie authentication.
	 */
	public interface CookieAuth extends DataUser {
		/**
		 * @return value used to identify user; may be e-mail or other identifier.
		 */
		public String getUsername();

		/** 
		 * @param location IP address or other identifier
		 * @return restricted token as URL-safe hash for user, password, and location parameter
		 */
		public String getToken(String location);
	}
}

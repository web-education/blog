/*
 * Copyright © "Open Digital Education" (SAS “WebServices pour l’Education”), 2014
 *
 * This program is published by "Open Digital Education" (SAS “WebServices pour l’Education”).
 * You must indicate the name of the software and the company in any production /contribution
 * using the software and indicate on the home page of the software industry in question,
 * "powered by Open Digital Education" with a reference to the website: https: //opendigitaleducation.com/.
 *
 * This program is free software, licensed under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of the License.
 *
 * You can redistribute this application and/or modify it since you respect the terms of the GNU Affero General Public License.
 * If you modify the source code and then use this modified source code in your creation, you must make available the source code of your modifications.
 *
 * You should have received a copy of the GNU Affero General Public License along with the software.
 * If not, please see : <http://www.gnu.org/licenses/>. Full compliance requires reading the terms of this license and following its directives.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.blog.services;


import fr.wseduc.webutils.Either;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;

public interface BlogService {

	enum CommentType { NONE, IMMEDIATE, RESTRAINT };

	enum PublishType { IMMEDIATE, RESTRAINT };

	List<String> FIELDS = Arrays.asList("author", "title", "description",
			"thumbnail", "comment-type", "created", "modified", "shared", "publish-type", "visibility", "slug");

	List<String> UPDATABLE_FIELDS = Arrays.asList("title", "description",
			"thumbnail", "comment-type", "modified", "publish-type", "trashed", "visibility", "slug");

	void create(JsonObject blog, UserInfos author, boolean isPublic, Handler<Either<String, JsonObject>> result);

	void update(String blogId, JsonObject blog, Handler<Either<String, JsonObject>> result);

	void delete(String blogId, Handler<Either<String, JsonObject>> result);

	void get(String blogId, Handler<Either<String, JsonObject>> result);

	void getPublic(String slug, Handler<Either<String, JsonObject>> result);

	void list(UserInfos user, final Integer page, final String search, Handler<Either<String, JsonArray>> result);

}

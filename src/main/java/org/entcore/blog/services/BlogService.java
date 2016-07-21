/*
 * Copyright © WebServices pour l'Éducation, 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.entcore.blog.services;


import fr.wseduc.webutils.Either;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;

public interface BlogService {

	enum CommentType { NONE, IMMEDIATE, RESTRAINT };

	enum PublishType { IMMEDIATE, RESTRAINT };

	List<String> FIELDS = Arrays.asList("author", "title", "description",
			"thumbnail", "comment-type", "created", "modified", "shared", "publish-type");

	List<String> UPDATABLE_FIELDS = Arrays.asList("title", "description",
			"thumbnail", "comment-type", "modified", "publish-type");

	void create(JsonObject blog, UserInfos author, Handler<Either<String, JsonObject>> result);

	void update(String blogId, JsonObject blog, Handler<Either<String, JsonObject>> result);

	void delete(String blogId, Handler<Either<String, JsonObject>> result);

	void get(String blogId, Handler<Either<String, JsonObject>> result);

	void list(UserInfos user, Handler<Either<String, JsonArray>> result);

}

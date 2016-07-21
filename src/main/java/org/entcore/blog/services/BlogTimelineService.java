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

import org.entcore.common.user.UserInfos;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;

public interface BlogTimelineService {

	void notifyShare(HttpServerRequest request, String blogId, UserInfos user,
					 JsonArray recipients, String blogUri);

	void notifyPublishPost(HttpServerRequest request, String blogId, String postId,
						   UserInfos user, String blogUri);

	void notifyPublishComment(HttpServerRequest request, String blogId, String postId,
								UserInfos user, String blogUri);

	void notifySubmitPost(HttpServerRequest request, String blogId,
			String postId, UserInfos user, String resourceUri);

}

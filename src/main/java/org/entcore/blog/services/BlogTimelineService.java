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

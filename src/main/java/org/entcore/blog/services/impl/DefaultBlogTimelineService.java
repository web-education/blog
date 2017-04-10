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

package org.entcore.blog.services.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.entcore.blog.services.BlogTimelineService;
import org.entcore.common.mongodb.MongoDbResult;
import org.entcore.common.neo4j.Neo;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.Config;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Container;

import com.mongodb.QueryBuilder;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.collections.Joiner;
import fr.wseduc.webutils.http.Renders;

public class DefaultBlogTimelineService implements BlogTimelineService {

	private final Neo neo;
	private final MongoDb mongo;
	private final TimelineHelper notification;

	public DefaultBlogTimelineService(Vertx vertx, EventBus eb, Container container, Neo neo, MongoDb mongo) {
		this.neo = neo;
		this.mongo = mongo;
		this.notification = new TimelineHelper(vertx, eb, container);
	}

	@Override
	public void notifyShare(final HttpServerRequest request, final String blogId, final UserInfos user,
			final JsonArray sharedArray, final String resourceUri) {
		if (sharedArray != null && user != null && blogId != null && request != null && resourceUri != null) {
			QueryBuilder query = QueryBuilder.start("_id").is(blogId);
			JsonObject keys = new JsonObject().putNumber("title", 1);
			mongo.findOne("blogs", MongoQueryBuilder.build(query), keys, new Handler<Message<JsonObject>>() {
				@Override
				public void handle(final Message<JsonObject> event) {
					if ("ok".equals(event.body().getString("status"))) {
						List<String> shareIds = getSharedIds(sharedArray);
						if (!shareIds.isEmpty()) {
							Map<String, Object> params = new HashMap<>();
							params.put("userId", user.getUserId());
							neo.send(neoQuery(shareIds), params, new Handler<Message<JsonObject>>() {
								@Override
								public void handle(Message<JsonObject> res) {
									if ("ok".equals(res.body().getString("status"))) {
										JsonObject r = res.body().getObject("result");
										List<String> recipients = new ArrayList<>();
										for (String attr: r.getFieldNames()) {
											String id = r.getObject(attr).getString("id");
											if (id != null) {
												recipients.add(id);
											}
										}
										String blogTitle = event.body()
												.getObject("result", new JsonObject()).getString("title");
										JsonObject p = new JsonObject()
												.putString("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
												.putString("username", user.getUsername())
												.putString("blogTitle", blogTitle)
												.putString("resourceUri", resourceUri);
										notification.notifyTimeline(request, "blog.share", user, recipients, blogId, p);
									}
								}
							});
						}
					}
				}
			});
		}
	}

	@Override
	public void notifySubmitPost(final HttpServerRequest request, final String blogId, final String postId,
			final UserInfos user, final String resourceUri) {
		if (resourceUri != null && user != null && blogId != null && request != null) {
			QueryBuilder blogQuery = QueryBuilder.start("_id").is(blogId);
			JsonObject blogKeys = new JsonObject().putNumber("author", 1);
			mongo.findOne("blogs", MongoQueryBuilder.build(blogQuery), blogKeys, new Handler<Message<JsonObject>>() {
				@Override
				public void handle(final Message<JsonObject> event) {
					if ("ok".equals(event.body().getString("status"))) {
						final String authorId = event.body()
								.getObject("result", new JsonObject())
								.getObject("author", new JsonObject())
								.getString("userId");

						final QueryBuilder query = QueryBuilder.start("_id").is(postId);
						final JsonObject keys = new JsonObject().putNumber("title", 1).putNumber("blog", 1);
						final JsonArray fetch = new JsonArray().addString("blog");

						mongo.findOne("posts", MongoQueryBuilder.build(query), keys, fetch,
								MongoDbResult.validResultHandler(new Handler<Either<String,JsonObject>>() {
							public void handle(Either<String, JsonObject> event) {
								if(event.isLeft())
									return;

								final JsonObject post = event.right().getValue();

								findRecipiants("posts", query, keys, fetch, "org-entcore-blog-controllers-PostController|publish", user, new Handler<Map<String, Object>>() {
									@Override
									public void handle(Map<String, Object> event) {
										List<String> recipients = new ArrayList<>();
										if (event != null)
											recipients = (List<String>) event.get("recipients");

										recipients.add(authorId);
										JsonObject p = new JsonObject()
												.putString("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
												.putString("username", user.getUsername())
												.putString("blogTitle", post.getObject("blog", new JsonObject()).getString("title"))
												.putString("blogUri", resourceUri)
												.putString("postTitle", post.getString("title"))
												.putString("postUri", resourceUri + "/" + postId)
												.putString("resourceUri", resourceUri + "/" + postId);
										notification.notifyTimeline(request, "blog.submit-post", user, recipients, blogId, postId, p);

									}
								});
							}
						}));
					}
				}
			});
		}
	}

	@Override
	public void notifyPublishPost(final HttpServerRequest request, final String blogId, final String postId,
			final UserInfos user, final String resourceUri) {
		if (resourceUri != null && user != null && blogId != null && request != null) {
			QueryBuilder query = QueryBuilder.start("_id").is(postId);
			JsonObject keys = new JsonObject().putNumber("title", 1).putNumber("blog", 1);
			JsonArray fetch = new JsonArray().addString("blog");
			findRecipiants("posts", query, keys, fetch, user, new Handler<Map<String, Object>>() {
				@Override
				public void handle(Map<String, Object> event) {
					if (event != null) {
						List<String> recipients = (List<String>) event.get("recipients");
						JsonObject blog = (JsonObject) event.get("blog");
						if (recipients != null) {
							JsonObject p = new JsonObject()
									.putString("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
									.putString("username", user.getUsername())
									.putString("blogTitle", blog.getObject("blog", new JsonObject()).getString("title"))
									.putString("blogUri", resourceUri)
									.putString("postTitle", blog.getString("title"))
									.putString("postUri", resourceUri + "/" + postId)
									.putString("resourceUri", resourceUri + "/" + postId);
							notification.notifyTimeline(request, "blog.publish-post", user, recipients, blogId, postId, p);
						}
					}
				}
			});
		}
	}

	@Override
	public void notifyPublishComment(final HttpServerRequest request, final String blogId, final String postId,
			final UserInfos user, final String resourceUri) {
		if (resourceUri != null && user != null && blogId != null && request != null) {
			QueryBuilder query = QueryBuilder.start("_id").is(postId);
			JsonObject keys = new JsonObject().putNumber("title", 1).putNumber("blog", 1);
			JsonArray fetch = new JsonArray().addString("blog");
			findRecipiants("posts", query, keys, fetch, user, new Handler<Map<String, Object>>() {
				@Override
				public void handle(Map<String, Object> event) {
					if (event != null) {
						List<String> recipients = (List<String>) event.get("recipients");
						JsonObject blog = (JsonObject) event.get("blog");
						String ownerId = blog.getObject("blog", new JsonObject())
								.getObject("author", new JsonObject())
								.getString("userId");
						if(ownerId != null && !ownerId.equals(user.getUserId())){
							if(recipients == null){
								recipients = new ArrayList<String>();
							}
							recipients.add(ownerId);
						}
						if (recipients != null) {
							JsonObject p = new JsonObject()
									.putString("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
									.putString("username", user.getUsername())
									.putString("blogTitle", blog.getObject("blog",
											new JsonObject()).getString("title"))
									.putString("blogUri", resourceUri)
									.putString("postTitle", blog.getString("title"))
									.putString("postUri", resourceUri + "/" + postId)
									.putString("resourceUri", resourceUri + "/" + postId);
							notification.notifyTimeline(request, "blog.publish-comment", user, recipients, blogId, postId, p);
						}
					}
				}
			});
		}
	}

	private void findRecipiants(String collection, QueryBuilder query, JsonObject keys,
			final JsonArray fetch, final UserInfos user, final Handler<Map<String, Object>> handler) {
		findRecipiants(collection, query, keys, fetch, null, user, handler);
	}
	private void findRecipiants(String collection, QueryBuilder query, JsonObject keys,
			final JsonArray fetch, final String filterRights, final UserInfos user,
				final Handler<Map<String, Object>> handler) {
		mongo.findOne(collection, MongoQueryBuilder.build(query), keys, fetch, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				if ("ok".equals(event.body().getString("status"))) {
					final JsonObject blog = event.body().getObject("result", new JsonObject());
					JsonArray shared;
					if (fetch == null) {
						shared = blog.getArray("shared");
					} else {
						shared = blog.getObject("blog", new JsonObject()).getArray("shared");
					}
					if (shared != null) {
						shared.add(blog.getObject("blog", new JsonObject()).getObject("author")); //Allows owner to get notified for contributors posts
						List<String> shareIds = getSharedIds(shared, filterRights);
						if (!shareIds.isEmpty()) {
							Map<String, Object> params = new HashMap<>();
							params.put("userId", user.getUserId());
							neo.send(neoQuery(shareIds), params, new Handler<Message<JsonObject>>() {
								@Override
								public void handle(Message<JsonObject> res) {
									if ("ok".equals(res.body().getString("status"))) {
										JsonObject r = res.body().getObject("result");
										List<String> recipients = new ArrayList<>();
										for (String attr: r.getFieldNames()) {
											String id = r.getObject(attr).getString("id");
											if (id != null) {
												recipients.add(id);
											}
										}
										Map<String, Object> t = new HashMap<>();
										t.put("recipients", recipients);
										t.put("blog", blog);
										handler.handle(t);
									} else {
										handler.handle(null);
									}
								}
							});
						} else {
							handler.handle(null);
						}
					} else {
						handler.handle(null);
					}
				} else {
					handler.handle(null);
				}
			}
		});
	}

	private List<String> getSharedIds(JsonArray shared){
		return getSharedIds(shared, null);
	}
	private List<String> getSharedIds(JsonArray shared, String filterRights) {
		List<String> shareIds = new ArrayList<>();
		for (Object o : shared) {
			if (!(o instanceof JsonObject)) continue;
			JsonObject userShared = (JsonObject) o;

			if(filterRights != null && !userShared.getBoolean(filterRights, false))
				continue;

			String userOrGroupId = userShared.getString("groupId",
					userShared.getString("userId"));
			if (userOrGroupId != null && !userOrGroupId.trim().isEmpty()) {
				shareIds.add(userOrGroupId);
			}
		}
		return shareIds;
	}

	private String neoQuery(List<String> shareIds) {
		return "MATCH n<-[:IN*0..1]-(u:User) " +
				"WHERE (n:User OR n:Group) AND n.id IN ['" +
				Joiner.on("','").join(shareIds) + "'] AND u.id <> {userId} " +
				"RETURN distinct u.id as id ";
	}

}

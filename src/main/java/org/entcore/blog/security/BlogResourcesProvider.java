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

package org.entcore.blog.security;

import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import org.entcore.blog.controllers.BlogController;
import org.entcore.blog.controllers.PostController;
import org.entcore.blog.services.PostService;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.http.Binding;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.StringUtils;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class BlogResourcesProvider implements ResourcesProvider {

	private MongoDb mongo = MongoDb.getInstance();

	@Override
	public void authorize(HttpServerRequest request, Binding binding, UserInfos user, Handler<Boolean> handler) {
		final String serviceMethod = binding.getServiceMethod();
		if (serviceMethod != null && serviceMethod.startsWith(BlogController.class.getName())) {
			String method = serviceMethod.substring(BlogController.class.getName().length() + 1);
			switch (method) {
			case "update":
			case "delete":
			case "get":
			case "shareResource":
			case "publishToLibrary":
			case "shareJson":
			case "shareJsonSubmit":
			case "removeShare":
				authorizeBlog(request, user, binding.getServiceMethod(), handler);
				break;
			default:
				handler.handle(false);
			}
		} else if (serviceMethod != null && serviceMethod.startsWith(PostController.class.getName())) {
			String method = serviceMethod.substring(PostController.class.getName().length() + 1);
			switch (method) {
			case "get":
				authorizeGetPost(request, user, binding.getServiceMethod(), handler);
				break;
			case "list":
			case "create":
			case "submit":
			case "publish":
			case "comments":
			case "comment":
			case "updateComment":
			case "deleteComment":
			case "publishComment":
				authorizeBlog(request, user, binding.getServiceMethod(), handler);
				break;
			case "update":
			case "delete":
			case "unpublish":
				hasRightOnPost(request, user, handler, serviceMethod.replaceAll("\\.", "-"));
				break;
			default:
				handler.handle(false);
			}
		} else {
			handler.handle(false);
		}
	}

	private void authorizeBlog(HttpServerRequest request, UserInfos user, String serviceMethod,
			Handler<Boolean> handler) {
		String id = request.params().get("blogId");
		if (id != null && !id.trim().isEmpty()) {
			QueryBuilder query = getDefaultQueryBuilder(user, serviceMethod, id);
			executeCountQuery(request, "blogs", MongoQueryBuilder.build(query), 1, handler);
		} else {
			handler.handle(false);
		}
	}

	private QueryBuilder getDefaultQueryBuilder(UserInfos user, String serviceMethod, String id) {
		List<DBObject> groups = new ArrayList<>();
		groups.add(QueryBuilder.start("userId").is(user.getUserId()).put(serviceMethod.replaceAll("\\.", "-")).is(true)
				.get());
		groups.add(QueryBuilder.start("userId").is(user.getUserId()).put("manager").is(true).get());
		for (String gpId : user.getGroupsIds()) {
			groups.add(QueryBuilder.start("groupId").is(gpId).put(serviceMethod.replaceAll("\\.", "-")).is(true).get());
			groups.add(QueryBuilder.start("groupId").is(gpId).put("manager").is(true).get());
		}
		return QueryBuilder.start("_id").is(id).or(QueryBuilder.start("author.userId").is(user.getUserId()).get(),
				QueryBuilder.start("shared")
						.elemMatch(new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()).get());
	}

	private void authorizeGetPost(HttpServerRequest request, final UserInfos user, String serviceMethod,
			final Handler<Boolean> handler) {
		String blogId = request.params().get("blogId");
		String postId = request.params().get("postId");
		if (blogId != null && !blogId.trim().isEmpty() && postId != null && !postId.trim().isEmpty()) {
			PostService.StateType state = getStateType(request);
			if (PostService.StateType.PUBLISHED.equals(state)) {
				QueryBuilder query = getDefaultQueryBuilder(user, serviceMethod, blogId);
				executeCountQuery(request, "blogs", MongoQueryBuilder.build(query), 1, handler);
			} else {
				//if not published, can i submit it?
				hasRightOnPost(request, user, handler, PostController.SUBMIT_ACTION);
			}
		} else {
			handler.handle(false);
		}
	}

	private void hasRightOnPost(final HttpServerRequest request, final UserInfos user, final Handler<Boolean> handler,
			String action) {
		String postId = request.params().get("postId");
		if (StringUtils.isEmpty(postId)) {
			handler.handle(false);
			return;
		}
		//
		QueryBuilder query = QueryBuilder.start("_id").is(postId);
		request.pause();
		mongo.findOne("posts", MongoQueryBuilder.build(query), null, new JsonArray().add("blog"),
				new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> event) {
						request.resume();
						if ("ok".equals(event.body().getString("status"))) {
							JsonObject res = event.body().getJsonObject("result");
							if (res == null) {
								handler.handle(false);
								return;
							}
							/**
							 * Is author?
							 */
							if (res.getJsonObject("author") != null
									&& user.getUserId().equals(res.getJsonObject("author").getString("userId"))) {
								handler.handle(true);
								return;
							}
							if (res.getJsonObject("blog") != null
									&& res.getJsonObject("blog").getJsonArray("shared") != null) {
								/**
								 * is author?
								 */
								String blogAuthorId = res.getJsonObject("blog")
										.getJsonObject("author", new JsonObject()).getString("userId");
								if (blogAuthorId != null && blogAuthorId.equals(user.getUserId())) {
									handler.handle(true);
									return;
								}
								/**
								 * has right action?
								 */
								for (Object o : res.getJsonObject("blog").getJsonArray("shared")) {
									if (!(o instanceof JsonObject))
										continue;
									JsonObject json = (JsonObject) o;
									if (json != null && json.getBoolean(action, false)
											&& (user.getUserId().equals(json.getString("userId"))
													|| user.getGroupsIds().contains(json.getString("groupId")))) {
										handler.handle(true);
										return;
									}
								}
							}
							handler.handle(false);
						}
					}
				});
	}

	public static PostService.StateType getStateType(HttpServerRequest request) {
		String s = request.params().get("state");
		PostService.StateType state;
		if (s == null || s.trim().isEmpty()) {
			state = PostService.StateType.PUBLISHED;
		} else {
			try {
				state = PostService.StateType.valueOf(s.toUpperCase());
			} catch (IllegalArgumentException | NullPointerException e) {
				state = PostService.StateType.PUBLISHED;
			}
		}
		return state;
	}

	private void executeCountQuery(final HttpServerRequest request, String collection, JsonObject query,
			final int expectedCountResult, final Handler<Boolean> handler) {
		request.pause();
		mongo.count(collection, query, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				request.resume();
				JsonObject res = event.body();
				handler.handle(res != null && "ok".equals(res.getString("status"))
						&& expectedCountResult == res.getInteger("count"));
			}
		});
	}

}

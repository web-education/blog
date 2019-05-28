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

package org.entcore.blog.controllers;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.user.UserUtils.getUserInfos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.entcore.blog.Blog;
import org.entcore.blog.services.BlogService;
import org.entcore.blog.services.BlogTimelineService;
import org.entcore.blog.services.PostService;
import org.entcore.blog.services.impl.DefaultBlogService;
import org.entcore.blog.services.impl.DefaultBlogTimelineService;
import org.entcore.blog.services.impl.DefaultPostService;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.request.ActionsUtils;
import org.entcore.common.neo4j.Neo;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.share.ShareService;
import org.entcore.common.share.impl.MongoDbShareService;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.ResourceUtils;
import org.entcore.common.utils.StringUtils;
import org.vertx.java.core.http.RouteMatcher;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class BlogController extends BaseController {

	private BlogService blog;
	private PostService postService;
	private BlogTimelineService timelineService;
	private ShareService shareService;
	private EventStore eventStore;

	private enum BlogEvent {
		ACCESS
	}

	public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
			Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);
		MongoDb mongo = MongoDb.getInstance();
		this.blog = new DefaultBlogService(mongo, config.getInteger("blog-paging-size", 30),
				config.getInteger("blog-search-word-min-size", 4));
		this.postService = new DefaultPostService(mongo, config.getInteger("post-search-word-min-size", 4), PostController.LIST_ACTION);
		this.timelineService = new DefaultBlogTimelineService(vertx, eb, config, new Neo(vertx, eb, log), mongo);
		final Map<String, List<String>> groupedActions = new HashMap<>();
		groupedActions.put("manager", loadManagerActions(securedActions.values()));
		this.shareService = new MongoDbShareService(eb, mongo, "blogs", securedActions, groupedActions);
		eventStore = EventStoreFactory.getFactory().getEventStore(Blog.class.getSimpleName());
	}

	@Get("")
	@SecuredAction("blog.view")
	public void blog(HttpServerRequest request) {
		renderView(request);
		eventStore.createAndStoreEvent(BlogEvent.ACCESS.name(), request);
	}

	@Get("/print/blog")
	@SecuredAction("blog.print")
	public void print(HttpServerRequest request) {
		renderView(request, new JsonObject().put("printBlogId", request.params().get("blog")), "print.html", null);
	}

	// TODO improve fields matcher and validater
	@Post("")
	@SecuredAction("blog.create")
	public void create(final HttpServerRequest request) {
		RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
			public void handle(final JsonObject data) {
				getUserInfos(eb, request, new Handler<UserInfos>() {
					@Override
					public void handle(final UserInfos user) {
						if (user != null) {
							blog.create(data, user, false, defaultResponseHandler(request));
						} else {
							unauthorized(request);
						}
					}
				});
			}
		});
	}

	@Put("/:blogId")
	@SecuredAction(value = "blog.manager", type = ActionType.RESOURCE)
	public void update(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		if (blogId == null || blogId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		Handler<Either<String, JsonObject>> handler = event -> {
			if (event.isRight()) {
				renderJson(request, event.right().getValue());
			} else {
				JsonObject error = new JsonObject().put("error", event.left().getValue());
				renderJson(request, error, 400);
			}
		};
		RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
			public void handle(JsonObject data) {
				getUserInfos(eb, request, user -> {
					if (user != null) {
						String visibility = data.getString("visibility");
						boolean changeVisibility = VisibilityFilter.PUBLIC.equals(visibility);
						if (changeVisibility) {
							changeResourcesVisibility(blogId, user, visibility, bool -> {
								if (bool) {
									blog.update(blogId, data, handler);
								}
							});
						} else {
							blog.update(blogId, data, handler);
						}
					} else {
						unauthorized(request);
					}
				});
			}
		});
	}

	private String getBlogUri(HttpServerRequest request, String blogId) {
		return pathPrefix + "#/view/" + blogId;
	}

	@Delete("/:blogId")
	@SecuredAction(value = "blog.manager", type = ActionType.RESOURCE)
	public void delete(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		if (blogId == null || blogId.trim().isEmpty()) {
			badRequest(request);
			return;
		}

		blog.delete(blogId, new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					renderJson(request, event.right().getValue(), 204);
				} else {
					JsonObject error = new JsonObject().put("error", event.left().getValue());
					renderJson(request, error, 400);
				}
			}
		});
	}

	@Get("/:blogId")
	@SecuredAction(value = "blog.read", type = ActionType.RESOURCE)
	public void get(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		if (blogId == null || blogId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		blog.get(blogId, defaultResponseHandler(request));
	}

	@Get("/counter/:blogId")
	@SecuredAction(value = "blog.posts.counter", type = ActionType.AUTHENTICATED)
	public void postCounter(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		if (StringUtils.isEmpty(blogId)) {
			badRequest(request);
			return;
		}

		getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					postService.counter(blogId, user, new Handler<Either<String, JsonArray>>() {
						public void handle(Either<String, JsonArray> event) {
							if (event.isLeft()) {
								arrayResponseHandler(request).handle(event);
								;
								return;
							}

							final JsonArray blogs = event.right().getValue();

							int countPublished = 0;
							int countDraft = 0;
							int countSubmitted = 0;
							int countAll = 0;

							final JsonObject result = new JsonObject();

							for (Object blogObj : blogs) {
								final String blogState = ((JsonObject) blogObj).getString("state");
								if (PostService.StateType.DRAFT.name().equals(blogState)) {
									countDraft++;
								} else if (PostService.StateType.PUBLISHED.name().equals(blogState)) {
									countPublished++;
								} else if (PostService.StateType.SUBMITTED.name().equals(blogState)) {
									countSubmitted++;
								}
							}

							countAll = countDraft + countPublished + countSubmitted;

							result.put("countPublished", countPublished);
							result.put("countDraft", countDraft);
							result.put("countSubmitted", countSubmitted);
							result.put("countAll", countAll);

							Renders.renderJson(request, result);
						}
					});
				} else {
					unauthorized(request);
				}
			}
		});
	}

	@Get("/list/all")
	@SecuredAction("blog.list")
	public void list(final HttpServerRequest request) {
		getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					final Integer page;

					try {
						page = (request.params().get("page") != null) ? Integer.parseInt(request.params().get("page"))
								: null;
					} catch (NumberFormatException e) {
						badRequest(request, e.getMessage());
						return;
					}

					final String search = request.params().get("search");
					final String excludePost = request.params().get("excludePost");

					blog.list(user, page, search, new Handler<Either<String, JsonArray>>() {
						public void handle(Either<String, JsonArray> event) {
							if (event.isLeft()) {
								arrayResponseHandler(request).handle(event);
								;
								return;
							}

							final JsonArray blogs = event.right().getValue();

							if (blogs.size() < 1) {
								renderJson(request, new JsonArray());
								return;
							}

							final AtomicInteger countdown = new AtomicInteger(blogs.size());
							final Handler<Void> finalHandler = new Handler<Void>() {
								public void handle(Void v) {
									if (countdown.decrementAndGet() <= 0) {
										renderJson(request, blogs);
									}
								}
							};
							if("true".equals(excludePost)){
								renderJson(request, blogs);
								return;
							}

							for (Object blogObj : blogs) {
								final JsonObject blog = (JsonObject) blogObj;

								postService.list(blog.getString("_id"), PostService.StateType.PUBLISHED, user, null, 2,
										null, new Handler<Either<String, JsonArray>>() {
											public void handle(Either<String, JsonArray> event) {
												if (event.isRight()) {
													blog.put("fetchPosts", event.right().getValue());
												}
												finalHandler.handle(null);
											}
										});
							}

						}
					});
				} else {
					unauthorized(request);
				}
			}
		});
	}

	@Get("/linker")
	public void listBlogsIds(final HttpServerRequest request) {
		getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					blog.list(user, null, null, new Handler<Either<String, JsonArray>>() {
						public void handle(Either<String, JsonArray> event) {
							if (event.isLeft()) {
								arrayResponseHandler(request).handle(event);
								return;
							}

							final JsonArray blogs = event.right().getValue();

							if (blogs.size() < 1) {
								renderJson(request, new JsonArray());
								return;
							}

							final AtomicInteger countdown = new AtomicInteger(blogs.size());
							final Handler<Void> finalHandler = new Handler<Void>() {
								public void handle(Void v) {
									if (countdown.decrementAndGet() <= 0) {
										renderJson(request, blogs);
									}
								}
							};

							for (Object blogObj : blogs) {
								final JsonObject blog = (JsonObject) blogObj;

								postService.list(blog.getString("_id"), PostService.StateType.PUBLISHED, user, null, 0,
										null, new Handler<Either<String, JsonArray>>() {
											public void handle(Either<String, JsonArray> event) {
												if (event.isRight()) {
													blog.put("fetchPosts", event.right().getValue());
												}
												finalHandler.handle(null);
											}
										});
							}

						}
					});
				} else {
					unauthorized(request);
				}
			}
		});
	}

	@Get("/share/json/:blogId")
	@SecuredAction(value = "blog.manager", type = ActionType.RESOURCE)
	public void shareJson(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		if (blogId == null || blogId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(UserInfos user) {
				if (user != null) {
					shareService.shareInfos(user.getUserId(), blogId, I18n.acceptLanguage(request),
							request.params().get("search"), defaultResponseHandler(request));
				} else {
					unauthorized(request);
				}
			}
		});
	}

	@Put("/share/json/:blogId")
	@SecuredAction(value = "blog.manager", type = ActionType.RESOURCE)
	public void shareJsonSubmit(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		if (blogId == null || blogId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		request.setExpectMultipart(true);
		request.endHandler(new Handler<Void>() {
			@Override
			public void handle(Void v) {
				final List<String> actions = request.formAttributes().getAll("actions");
				final String groupId = request.formAttributes().get("groupId");
				final String userId = request.formAttributes().get("userId");
				if (actions == null || actions.isEmpty()) {
					badRequest(request);
					return;
				}
				getUserInfos(eb, request, new Handler<UserInfos>() {
					@Override
					public void handle(final UserInfos user) {
						if (user != null) {
							Handler<Either<String, JsonObject>> r = new Handler<Either<String, JsonObject>>() {
								@Override
								public void handle(Either<String, JsonObject> event) {
									if (event.isRight()) {
										JsonObject n = event.right().getValue().getJsonObject("notify-timeline");
										if (n != null) {
											timelineService.notifyShare(request, blogId, user, new JsonArray().add(n),
													getBlogUri(request, blogId));
										}
										renderJson(request, event.right().getValue());
									} else {
										JsonObject error = new JsonObject().put("error", event.left().getValue());
										renderJson(request, error, 400);
									}
								}
							};
							if (groupId != null) {
								shareService.groupShare(user.getUserId(), groupId, blogId, actions, r);
							} else if (userId != null) {
								shareService.userShare(user.getUserId(), userId, blogId, actions, r);
							} else {
								badRequest(request);
							}
						} else {
							unauthorized(request);
						}
					}
				});
			}
		});
	}

	@Put("/share/remove/:blogId")
	@SecuredAction(value = "blog.manager", type = ActionType.RESOURCE)
	public void removeShare(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		if (blogId == null || blogId.trim().isEmpty()) {
			badRequest(request);
			return;
		}

		request.setExpectMultipart(true);
		request.endHandler(new Handler<Void>() {
			@Override
			public void handle(Void v) {
				final List<String> actions = request.formAttributes().getAll("actions");
				final String groupId = request.formAttributes().get("groupId");
				final String userId = request.formAttributes().get("userId");
				if (groupId != null) {
					shareService.removeGroupShare(groupId, blogId, actions, defaultResponseHandler(request));
				} else if (userId != null) {
					shareService.removeUserShare(userId, blogId, actions, defaultResponseHandler(request));
				} else {
					badRequest(request);
				}
			}
		});
	}

	@Put("/share/resource/:blogId")
	@SecuredAction(value = "blog.manager", type = ActionType.RESOURCE)
	public void shareResource(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, share -> {
						shareService.share(user.getUserId(), blogId, share, r -> {
							if (r.isRight()) {
								JsonArray nta = r.right().getValue().getJsonArray("notify-timeline-array");
								if (nta != null) {
									timelineService.notifyShare(request, blogId, user, nta,
											getBlogUri(request, blogId));
								}
								renderJson(request, r.right().getValue());
							} else {
								JsonObject error = new JsonObject().put("error", r.left().getValue());
								renderJson(request, error, 400);
							}
						});
					});
				} else {
					unauthorized(request);
				}
			}
		});
	}

	private List<String> loadManagerActions(Collection<fr.wseduc.webutils.security.SecuredAction> actions) {
		List<String> managerActions = new ArrayList<>();
		if (actions != null) {
			for (fr.wseduc.webutils.security.SecuredAction a : actions) {
				if (a.getName() != null && "RESOURCE".equals(a.getType())
						&& "blog.manager".equals(a.getDisplayName())) {
					managerActions.add(a.getName().replaceAll("\\.", "-"));
				}
			}
		}
		return managerActions;
	}

	@Get("/blog/availables-workflow-actions")
	@SecuredAction(value = "blog.habilitation", type = ActionType.AUTHENTICATED)
	public void getActionsInfos(final HttpServerRequest request) {
		ActionsUtils.findWorkflowSecureActions(eb, request, this);
	}

	// Routes for public blogs

	@Get("/pub/:slug")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
	public void getPublicBlogInfos(final HttpServerRequest request) {
		final String slug = request.params().get("slug");
		if (slug == null || slug.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		blog.getPublic(slug, defaultResponseHandler(request));
	}

	@Post("/pub")
	@SecuredAction("blog.public")
	public void createPublicBlog(final HttpServerRequest request) {
		RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
			public void handle(final JsonObject data) {
				getUserInfos(eb, request, new Handler<UserInfos>() {
					@Override
					public void handle(final UserInfos user) {
						if (user != null) {
							blog.create(data, user, true, defaultResponseHandler(request));
						} else {
							unauthorized(request);
						}
					}
				});
			}
		});
	}

	@Put("/pub/:blogId")
	@SecuredAction("blog.public")
	public void updatePublicBlog(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		if (blogId == null || blogId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
        RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
            public void handle(final JsonObject data) {
                getUserInfos(eb, request, new Handler<UserInfos>() {
                    @Override
                    public void handle(final UserInfos user) {
                        if (user != null) {
							String visibility = data.getString("visibility");
							boolean changeVisibility = !VisibilityFilter.PUBLIC.equals(visibility);
							if (changeVisibility) {
								changeResourcesVisibility(blogId, user, visibility, bool -> {
									if (bool) {
										blog.update(blogId, data, defaultResponseHandler(request));
									}
								});
							} else {
								blog.update(blogId, data, defaultResponseHandler(request));
							}
                        } else {
                            unauthorized(request);
                        }
                    }
                });
            }
        });
	}

	private void changeResourcesVisibility(String blogId, UserInfos user, String visibility, Handler<Boolean> handler) {
		postService.list(blogId, user, null, 0, "", null, event -> {
			if (event.isRight()) {
				JsonArray posts = event.right().getValue();
				posts.forEach(elem ->{
					JsonObject post = (JsonObject)(elem);
					String content = post.getString("content");
					List<String> resourcesList = ResourceUtils.extractIds(content);
                    JsonObject j = new JsonObject()
                            .put("action", "changeVisibility")
							.put("visibility", visibility)
                            .put("documentIds", new JsonArray(resourcesList));
                    eb.send("org.entcore.workspace", j, r -> handler.handle(r.succeeded()));
				});
			}
		});
	}

}

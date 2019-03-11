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

import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import org.entcore.blog.services.BlogService;
import org.entcore.blog.services.PostService;
import fr.wseduc.webutils.*;

import org.entcore.common.mongodb.MongoDbResult;
import org.entcore.common.service.impl.MongoDbSearchService;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.StringUtils;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;

public class DefaultPostService implements PostService {
	private final String listPostAction;
	private static final String MANAGER_ACTION = "org-entcore-blog-controllers-BlogController|shareResource";

	private final MongoDb mongo;
	protected static final String POST_COLLECTION = "posts";
	private static final JsonObject defaultKeys = new JsonObject()
			.put("author", 1)
			.put("title", 1)
			.put("content", 1)
			.put("state", 1)
			.put("created", 1)
			.put("modified", 1)
			.put("views", 1)
			.put("firstPublishDate", 1);

	private int searchWordMinSize;

	public DefaultPostService(MongoDb mongo, int searchWordMinSize,String listPostAction) {
		this.mongo = mongo;
		this.listPostAction = listPostAction;
		this.searchWordMinSize = searchWordMinSize;
	}

	@Override
	public void create(String blogId, JsonObject post, UserInfos author,
					   final Handler<Either<String, JsonObject>> result) {
		JsonObject now = MongoDb.now();
		JsonObject blogRef = new JsonObject()
				.put("$ref", "blogs")
				.put("$id", blogId);
		JsonObject owner = new JsonObject()
				.put("userId", author.getUserId())
				.put("username", author.getUsername())
				.put("login", author.getLogin());
		post.put("created", now)
				.put("modified", now)
				.put("author", owner)
				.put("state", StateType.DRAFT.name())
				.put("comments", new JsonArray())
				.put("views", 0)
				.put("blog", blogRef);
		JsonObject b = Utils.validAndGet(post, FIELDS, FIELDS);
		if (validationError(result, b)) return;
		b.put("sorted", now);
		if (b.containsKey("content")) {
			b.put("contentPlain",  StringUtils.stripHtmlTag(b.getString("content", "")));
		}
		mongo.save(POST_COLLECTION, b, MongoDbResult.validActionResultHandler(new Handler<Either<String,JsonObject>>() {
			public void handle(Either<String, JsonObject> event) {
				if(event.isLeft()){
					result.handle(event);
					return;
				}
				mongo.findOne(POST_COLLECTION,
					new JsonObject().put("_id", event.right().getValue().getString("_id")),
					MongoDbResult.validResultHandler(result));
			}
		}));
	}

	@Override
	public void update(String postId, final JsonObject post, final UserInfos user, final Handler<Either<String, JsonObject>> result) {

		final JsonObject jQuery = MongoQueryBuilder.build(QueryBuilder.start("_id").is(postId));
		mongo.findOne(POST_COLLECTION, jQuery,  MongoDbResult.validActionResultHandler(new Handler<Either<String,JsonObject>>() {
			public void handle(Either<String, JsonObject> event) {
				if(event.isLeft()){
					result.handle(event);
					return;
				} else {
					final JsonObject postFromDb = event.right().getValue().getJsonObject("result", new JsonObject());
					final JsonObject now = MongoDb.now();
					post.put("modified", now);
					final JsonObject b = Utils.validAndGet(post, UPDATABLE_FIELDS, Collections.<String>emptyList());

					if (validationError(result, b)) return;
					if (b.containsKey("content")) {
						b.put("contentPlain",  StringUtils.stripHtmlTag(b.getString("content", "")));
					}

					if (postFromDb.getJsonObject("firstPublishDate") != null) {
						b.put("sorted", postFromDb.getJsonObject("firstPublishDate"));
					} else {
						b.put("sorted", now);
					}

					//if user is author, draft state
					if (user.getUserId().equals(postFromDb.getJsonObject("author", new JsonObject()).getString("userId"))) {
						b.put("state", StateType.DRAFT.name());
					}

					MongoUpdateBuilder modifier = new MongoUpdateBuilder();
					for (String attr: b.fieldNames()) {
						modifier.set(attr, b.getValue(attr));
					}
					mongo.update(POST_COLLECTION, jQuery, modifier.build(),
							new Handler<Message<JsonObject>>() {
								@Override
								public void handle(Message<JsonObject> event) {
									if ("ok".equals(event.body().getString("status"))) {
										final JsonObject r = new JsonObject().put("state", b.getString("state", postFromDb.getString("state")));
										result.handle(new Either.Right<String, JsonObject>(r));
									} else {
										result.handle(new Either.Left<String, JsonObject>(event.body().getString("message", "")));
									}
								}
							});
				}
			}})
		);

	}

	@Override
	public void delete(String postId, final Handler<Either<String, JsonObject>> result) {
		QueryBuilder query = QueryBuilder.start("_id").is(postId);
		mongo.delete(POST_COLLECTION, MongoQueryBuilder.build(query),
				new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> event) {
						result.handle(Utils.validResult(event));
					}
				});
	}

	@Override
	public void get(String blogId, final String postId, StateType state,
				final Handler<Either<String, JsonObject>> result) {
		QueryBuilder query = QueryBuilder.start("_id").is(postId).put("blog.$id").is(blogId)
				.put("state").is(state.name());

		mongo.findOne(POST_COLLECTION, MongoQueryBuilder.build(query), defaultKeys,
				new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				Either<String, JsonObject> res = Utils.validResult(event);
				if (res.isRight() && res.right().getValue().size() > 0) {
					QueryBuilder query2 = QueryBuilder.start("_id").is(postId)
							.put("state").is(StateType.PUBLISHED.name());
					MongoUpdateBuilder incView = new MongoUpdateBuilder();
					incView.inc("views", 1);
					mongo.update(POST_COLLECTION, MongoQueryBuilder.build(query2), incView.build());
				}
				result.handle(res);
			}
		});
	}

	@Override
	public void list(String blogId, final UserInfos user, final Integer page, final int limit, final String search, final Set<String> states, final Handler<Either<String, JsonArray>> result) {
		final QueryBuilder accessQuery;
		if (states == null || states.isEmpty()) {
			accessQuery = QueryBuilder.start("blog.$id").is(blogId);
		} else {
			accessQuery = QueryBuilder.start("blog.$id").is(blogId).put("state").in(states);
		}

		final QueryBuilder isManagerQuery = getDefautQueryBuilderForList(blogId, user,true);
		final JsonObject sort = new JsonObject().put("sorted", -1);
		final JsonObject projection = defaultKeys.copy();
		projection.remove("content");

		final Handler<Message<JsonObject>> finalHandler = new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				result.handle(Utils.validResults(event));
			}
		};

		mongo.count("blogs", MongoQueryBuilder.build(isManagerQuery), new Handler<Message<JsonObject>>() {
			public void handle(Message<JsonObject> event) {
				JsonObject res = event.body();
				if(res == null || !"ok".equals(res.getString("status"))){
					result.handle(new Either.Left<String, JsonArray>(event.body().encodePrettily()));
					return;
				}
				boolean isManager = 1 == res.getInteger("count", 0);

				accessQuery.or(
					QueryBuilder.start("state").is(StateType.PUBLISHED.name()).get(),
					QueryBuilder.start().and(
						QueryBuilder.start("author.userId").is(user.getUserId()).get(),
						QueryBuilder.start("state").is(StateType.DRAFT.name()).get()
					).get(),
					isManager ?
						QueryBuilder.start("state").is(StateType.SUBMITTED.name()).get() :
						QueryBuilder.start().and(
							QueryBuilder.start("author.userId").is(user.getUserId()).get(),
							QueryBuilder.start("state").is(StateType.SUBMITTED.name()).get()
						).get()
				);

				final QueryBuilder query = getQueryListBuilder(search, result, accessQuery);

				if (query != null) {
					if (limit > 0 && page == null) {
						mongo.find(POST_COLLECTION, MongoQueryBuilder.build(query), sort, projection, 0, limit, limit, finalHandler);
					} else if (page == null) {
						mongo.find(POST_COLLECTION, MongoQueryBuilder.build(query), sort, projection, finalHandler);
					} else {
						final int skip = (0 == page) ? -1 : page * limit;
						mongo.find(POST_COLLECTION, MongoQueryBuilder.build(query), sort, projection, skip, limit, limit, finalHandler);
					}
				}
			}
		});
	}

	@Override
	public void counter(final String blogId, final UserInfos user,
	                    final Handler<Either<String, JsonArray>> result) {
		final QueryBuilder query = QueryBuilder.start("blog.$id").is(blogId);
		final QueryBuilder isManagerQuery = getDefautQueryBuilderForList(blogId, user,true);
		final JsonObject projection = new JsonObject();
		projection.put("state", 1);
		projection.put("_id", -1);

		final Handler<Message<JsonObject>> finalHandler = new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				result.handle(Utils.validResults(event));
			}
		};

		mongo.count("blogs", MongoQueryBuilder.build(isManagerQuery), new Handler<Message<JsonObject>>() {
			public void handle(Message<JsonObject> event) {
				JsonObject res = event.body();
				if (res == null || !"ok".equals(res.getString("status"))) {
					result.handle(new Either.Left<String, JsonArray>(event.body().encodePrettily()));
					return;
				}
				boolean isManager = 1 == res.getInteger("count", 0);

				query.or(
						QueryBuilder.start("state").is(StateType.PUBLISHED.name()).get(),
						QueryBuilder.start().and(
								QueryBuilder.start("author.userId").is(user.getUserId()).get(),
								QueryBuilder.start("state").is(StateType.DRAFT.name()).get()
						).get(),
						isManager ?
								QueryBuilder.start("state").is(StateType.SUBMITTED.name()).get() :
								QueryBuilder.start().and(
										QueryBuilder.start("author.userId").is(user.getUserId()).get(),
										QueryBuilder.start("state").is(StateType.SUBMITTED.name()).get()
								).get()
				);
    			mongo.find(POST_COLLECTION, MongoQueryBuilder.build(query), null, projection, finalHandler);
			}
		});
	}

	@Override
	public void list(String blogId, final StateType state, final UserInfos user, final Integer page, final int limit, final String search,
				final Handler<Either<String, JsonArray>> result) {
		final QueryBuilder accessQuery = QueryBuilder.start("blog.$id").is(blogId).put("state").is(state.name());
		final JsonObject sort = new JsonObject().put("sorted", -1);
		final JsonObject projection = defaultKeys.copy();
		projection.remove("content");

		final Handler<Message<JsonObject>> finalHandler = new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				result.handle(Utils.validResults(event));
			}
		};

		final QueryBuilder query = getQueryListBuilder(search, result, accessQuery);

		if (query != null) {

			if (StateType.PUBLISHED.equals(state)) {
				if (limit > 0 && page == null) {
					mongo.find(POST_COLLECTION, MongoQueryBuilder.build(query), sort, projection, 0, limit, limit, finalHandler);
				} else if (page == null) {
					mongo.find(POST_COLLECTION, MongoQueryBuilder.build(query), sort, projection, finalHandler);
				} else {
					final int skip = (0 == page) ? -1 : page * limit;
					mongo.find(POST_COLLECTION, MongoQueryBuilder.build(query), sort, projection, skip, limit, limit, finalHandler);
				}
			} else {
				QueryBuilder query2 = getDefautQueryBuilderForList(blogId, user,true);
				mongo.count("blogs", MongoQueryBuilder.build(query2), new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> event) {
						JsonObject res = event.body();

						if ((res != null && "ok".equals(res.getString("status")) &&
								1 != res.getInteger("count")) || StateType.DRAFT.equals(state)) {
							accessQuery.put("author.userId").is(user.getUserId());
						}

						final QueryBuilder listQuery = getQueryListBuilder(search, result, accessQuery);
						if (limit > 0 && page == null) {
							mongo.find(POST_COLLECTION, MongoQueryBuilder.build(listQuery), sort, projection, 0, limit, limit, finalHandler);
						} else if (page == null) {
							mongo.find(POST_COLLECTION, MongoQueryBuilder.build(listQuery), sort, projection, finalHandler);
						} else {
							final int skip = (0 == page) ? -1 : page * limit;
							mongo.find(POST_COLLECTION, MongoQueryBuilder.build(listQuery), sort, projection, skip, limit, limit, finalHandler);
						}
					}
				});
			}
		}
	}

	private QueryBuilder getQueryListBuilder(String search, Handler<Either<String, JsonArray>> result, QueryBuilder accessQuery) {
		final QueryBuilder query;
		if (!StringUtils.isEmpty(search)) {
			final List<String> searchWords = DefaultBlogService.checkAndComposeWordFromSearchText(search, this.searchWordMinSize);
			if (!searchWords.isEmpty()) {
				final QueryBuilder searchQuery = new QueryBuilder();
				searchQuery.text(MongoDbSearchService.textSearchedComposition(searchWords));
				query = new QueryBuilder().and(accessQuery.get(), searchQuery.get());
			} else {
				query = null;
				//empty result (no word to search)
				result.handle(new Either.Right<String, JsonArray>(new JsonArray()));
			}
		} else {
			query = accessQuery;
		}
		return query;
	}

	@Override
	public void listOne(String blogId, String postId, final UserInfos user, final Handler<Either<String, JsonArray>> result) {
		final QueryBuilder query = QueryBuilder.start("blog.$id").is(blogId).put("_id").is(postId);
		final QueryBuilder isManagerQuery = getDefautQueryBuilderForList(blogId, user,true);
		final JsonObject sort = new JsonObject().put("modified", -1);
		final JsonObject projection = defaultKeys.copy();
		projection.remove("content");

		final Handler<Message<JsonObject>> finalHandler = new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				result.handle(Utils.validResults(event));
			}
		};

		mongo.count("blogs", MongoQueryBuilder.build(isManagerQuery), new Handler<Message<JsonObject>>() {
			public void handle(Message<JsonObject> event) {
				JsonObject res = event.body();
				if(res == null || !"ok".equals(res.getString("status"))){
					result.handle(new Either.Left<String, JsonArray>(event.body().encodePrettily()));
					return;
				}
				boolean isManager = 1 == res.getInteger("count", 0);

				query.or(
						QueryBuilder.start("state").is(StateType.PUBLISHED.name()).get(),
						QueryBuilder.start().and(
								QueryBuilder.start("author.userId").is(user.getUserId()).get(),
								QueryBuilder.start("state").is(StateType.DRAFT.name()).get()
						).get(),
						isManager ?
								QueryBuilder.start("state").is(StateType.SUBMITTED.name()).get() :
								QueryBuilder.start().and(
										QueryBuilder.start("author.userId").is(user.getUserId()).get(),
										QueryBuilder.start("state").is(StateType.SUBMITTED.name()).get()
								).get()
				);
				mongo.find(POST_COLLECTION, MongoQueryBuilder.build(query), sort, projection, finalHandler);
			}
		});
	}

	private QueryBuilder getDefautQueryBuilderForList(String blogId, UserInfos user,boolean manager) {
		List<DBObject> groups = new ArrayList<>();
		if(manager) {
			groups.add(QueryBuilder.start("userId").is(user.getUserId())
					.put(MANAGER_ACTION).is(true).get());
		}else {
			groups.add(QueryBuilder.start("userId").is(user.getUserId())
					.put(this.listPostAction).is(true).get());
		}
		for (String gpId: user.getGroupsIds()) {
			if(manager) {
				groups.add(QueryBuilder.start("groupId").is(gpId)
						.put(MANAGER_ACTION).is(true).get());
			}else {
				groups.add(QueryBuilder.start("groupId").is(gpId)
						.put(this.listPostAction).is(true).get());
			}
		}
		return QueryBuilder.start("_id").is(blogId).or(
				QueryBuilder.start("author.userId").is(user.getUserId()).get(),
				QueryBuilder.start("shared").elemMatch(
				new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()).get()
		);
	}

	@Override
	public void submit(String blogId, String postId, UserInfos user, final Handler<Either<String, JsonObject>> result) {
		QueryBuilder query = QueryBuilder.start("_id").is(postId).put("blog.$id").is(blogId)
				.put("state").is(StateType.DRAFT.name()).put("author.userId").is(user.getUserId());
		final JsonObject q = MongoQueryBuilder.build(query);
		JsonObject keys = new JsonObject().put("blog", 1).put("firstPublishDate", 1);
		JsonArray fetch = new JsonArray().add("blog");
		mongo.findOne(POST_COLLECTION, q, keys, fetch,
				new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				final JsonObject res = event.body().getJsonObject("result", new JsonObject());
				if ("ok".equals(event.body().getString("status")) && res.size() > 0) {
					BlogService.PublishType type = Utils.stringToEnum(res
							.getJsonObject("blog",  new JsonObject()).getString("publish-type"),
							BlogService.PublishType.RESTRAINT, BlogService.PublishType.class);
					final StateType state = (BlogService.PublishType.RESTRAINT.equals(type)) ?
							StateType.SUBMITTED : StateType.PUBLISHED;
					MongoUpdateBuilder updateQuery = new MongoUpdateBuilder().set("state", state.name());

					// if IMMEDIATE published post, first publishing must define the first published date
					if (StateType.PUBLISHED.equals(state) && res.getJsonObject("firstPublishDate") == null) {
						updateQuery = updateQuery.set("firstPublishDate", MongoDb.now()).set("sorted",  MongoDb.now());
					}

					mongo.update(POST_COLLECTION, q, updateQuery.build(), new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> res) {
							res.body().put("state", state.name());
							result.handle(Utils.validResult(res));
						}
					});
				} else {
					result.handle(Utils.validResult(event));
				}
			}
		});
	}

	@Override
	public void publish(final String blogId, final String postId, final Handler<Either<String, JsonObject>> result) {
		QueryBuilder query = QueryBuilder.start("_id").is(postId).put("blog.$id").is(blogId);
		MongoUpdateBuilder updateQuery = new MongoUpdateBuilder().set("state", StateType.PUBLISHED.name());
		mongo.update(POST_COLLECTION, MongoQueryBuilder.build(query), updateQuery.build(),
				MongoDbResult.validActionResultHandler(new Handler<Either<String,JsonObject>>() {
			public void handle(Either<String, JsonObject> event) {
				if(event.isLeft()){
					result.handle(event);
					return;
				}

				QueryBuilder query = QueryBuilder
					.start("_id").is(postId)
					.put("blog.$id").is(blogId)
					.put("firstPublishDate").exists(false);

				MongoUpdateBuilder updateQuery = new MongoUpdateBuilder().set("firstPublishDate", MongoDb.now()).set("sorted",  MongoDb.now());

				mongo.update(POST_COLLECTION, MongoQueryBuilder.build(query), updateQuery.build(),
						MongoDbResult.validActionResultHandler(result));
			}
		}));
	}

	@Override
	public void unpublish(String postId, final Handler<Either<String, JsonObject>> result) {
		QueryBuilder query = QueryBuilder.start("_id").is(postId);
		MongoUpdateBuilder updateQuery = new MongoUpdateBuilder().set("state", StateType.DRAFT.name());
		mongo.update(POST_COLLECTION, MongoQueryBuilder.build(query), updateQuery.build(),
				new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> res) {
				result.handle(Utils.validResult(res));
			}
		});
	}

	@Override
	public void addComment(String blogId, String postId, final String comment, final UserInfos author,
			final Handler<Either<String, JsonObject>> result) {
		if (comment == null || comment.trim().isEmpty()) {
			result.handle(new Either.Left<String, JsonObject>("Validation error : invalid comment."));
			return;
		}
		QueryBuilder query = QueryBuilder.start("_id").is(postId).put("blog.$id").is(blogId);
		final JsonObject q = MongoQueryBuilder.build(query);
		JsonObject keys = new JsonObject().put("blog", 1);
		JsonArray fetch = new JsonArray().add("blog");
		mongo.findOne(POST_COLLECTION, q, keys, fetch, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				if ("ok".equals(event.body().getString("status")) &&
						event.body().getJsonObject("result", new JsonObject()).size() > 0) {
					BlogService.CommentType type = Utils.stringToEnum(event.body().getJsonObject("result")
							.getJsonObject("blog",  new JsonObject()).getString("comment-type"),
							BlogService.CommentType.RESTRAINT, BlogService.CommentType.class);
					if (BlogService.CommentType.NONE.equals(type)) {
						result.handle(new Either.Left<String, JsonObject>("Comments are disabled for this blog."));
						return;
					}
					StateType s = BlogService.CommentType.IMMEDIATE.equals(type) ?
							StateType.PUBLISHED : StateType.SUBMITTED;
					JsonObject user = new JsonObject()
							.put("userId", author.getUserId())
							.put("username", author.getUsername())
							.put("login", author.getLogin());
					JsonObject c = new JsonObject()
							.put("comment", comment)
							.put("id", UUID.randomUUID().toString())
							.put("state", s.name())
							.put("author", user)
							.put("created", MongoDb.now());
					MongoUpdateBuilder updateQuery = new MongoUpdateBuilder().push("comments", c);
					mongo.update(POST_COLLECTION, q, updateQuery.build(), new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> res) {
							result.handle(Utils.validResult(res));
						}
					});
				} else {
					result.handle(Utils.validResult(event));
				}
			}
		});
	}

	@Override
	public void updateComment(String postId, final String commentId, final String comment, final UserInfos coauthor,
						   final Handler<Either<String, JsonObject>> result) {
		if (comment == null || comment.trim().isEmpty()) {
			result.handle(new Either.Left<String, JsonObject>("Validation error : invalid comment."));
			return;
		}
		QueryBuilder query = QueryBuilder.start("_id").is(postId).put("comments").elemMatch(
				QueryBuilder.start("id").is(commentId).get());

		final JsonObject user = new JsonObject()
				.put("userId", coauthor.getUserId())
				.put("username", coauthor.getUsername())
				.put("login", coauthor.getLogin());

		MongoUpdateBuilder updateQuery = new MongoUpdateBuilder().set("comments.$.comment", comment);
		updateQuery = updateQuery.set("comments.$.coauthor", user).set("comments.$.modified", MongoDb.now());
		mongo.update(POST_COLLECTION, MongoQueryBuilder.build(query), updateQuery.build(), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> res) {
							result.handle(Utils.validResult(res));
						}
		});
	}

	@Override
	public void deleteComment(final String blogId, final String commentId, final UserInfos user,
			final Handler<Either<String, JsonObject>> result) {
		QueryBuilder query2 = getDefautQueryBuilderForList(blogId, user,false);
		mongo.count("blogs", MongoQueryBuilder.build(query2), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject res = event.body();
				QueryBuilder tmp = QueryBuilder.start("id").is(commentId);
				if (res != null && "ok".equals(res.getString("status")) &&
						1 != res.getInteger("count")) {
					tmp.put("author.userId").is(user.getUserId());
				}
				QueryBuilder query = QueryBuilder.start("blog.$id").is(blogId).put("comments").elemMatch(
					tmp.get()
				);
				JsonObject c = new JsonObject().put("id", commentId);
				MongoUpdateBuilder queryUpdate = new MongoUpdateBuilder().pull("comments", c);
				mongo.update(POST_COLLECTION, MongoQueryBuilder.build(query), queryUpdate.build(),
						new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> res) {
						result.handle(Utils.validResult(res));
					}
				});
			}
		});
	}

	@Override
	public void listComment(String blogId, String postId, final UserInfos user,
			final Handler<Either<String, JsonArray>> result) {
		final QueryBuilder query = QueryBuilder.start("_id").is(postId).put("blog.$id").is(blogId);
		JsonObject keys = new JsonObject().put("comments", 1).put("blog", 1);
		JsonArray fetch = new JsonArray().add("blog");
		mongo.findOne(POST_COLLECTION, MongoQueryBuilder.build(query), keys, fetch,
			new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> event) {
					JsonArray comments = new JsonArray();
					if ("ok".equals(event.body().getString("status")) &&
							event.body().getJsonObject("result", new JsonObject()).size() > 0) {
						JsonObject res = event.body().getJsonObject("result");
						boolean userIsManager = userIsManager(user, res.getJsonObject("blog"));
						for (Object o: res.getJsonArray("comments", new JsonArray())) {
							if (!(o instanceof JsonObject)) continue;
							JsonObject j = (JsonObject) o;
							if (userIsManager || StateType.PUBLISHED.name().equals(j.getString("state")) ||
									user.getUserId().equals(
											j.getJsonObject("author", new JsonObject()).getString("userId"))) {
								comments.add(j);
							}
						}
					}
					result.handle(new Either.Right<String, JsonArray>(comments));
				}
			});
	}

	private boolean userIsManager(UserInfos user, JsonObject res) {
		if (res != null  && res.getJsonArray("shared") != null) {
			for (Object o: res.getJsonArray("shared")) {
				if (!(o instanceof JsonObject)) continue;
				JsonObject json = (JsonObject) o;
				return json != null && json.getBoolean(MANAGER_ACTION, false) &&
						(user.getUserId().equals(json.getString("userId")) ||
								user.getGroupsIds().contains(json.getString("groupId")));
			}
		}
		return false;
	}

	@Override
	public void publishComment(String blogId, String commentId,
				final Handler<Either<String, JsonObject>> result) {
		QueryBuilder query = QueryBuilder.start("blog.$id").is(blogId).put("comments").elemMatch(
			QueryBuilder.start("id").is(commentId).get()
		);
		MongoUpdateBuilder updateQuery = new MongoUpdateBuilder().set("comments.$.state", StateType.PUBLISHED.name());
		mongo.update(POST_COLLECTION, MongoQueryBuilder.build(query), updateQuery.build(),
				new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> res) {
				result.handle(Utils.validResult(res));
			}
		});
	}

	private boolean validationError(Handler<Either<String, JsonObject>> result, JsonObject b) {
		if (b == null) {
			result.handle(new Either.Left<String, JsonObject>("Validation error : invalids fields."));
			return true;
		}
		return false;
	}

}

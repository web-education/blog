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
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.*;

public class DefaultPostService implements PostService {

	private final MongoDb mongo;
	protected static final String POST_COLLECTION = "posts";
	private static final JsonObject defaultKeys = new JsonObject()
			.putNumber("author", 1)
			.putNumber("title", 1)
			.putNumber("content", 1)
			.putNumber("state", 1)
			.putNumber("created", 1)
			.putNumber("modified", 1)
			.putNumber("views", 1)
			.putNumber("firstPublishDate", 1);

	private int searchWordMinSize;

	public DefaultPostService(MongoDb mongo, int searchWordMinSize) {
		this.mongo = mongo;
		this.searchWordMinSize = searchWordMinSize;
	}

	@Override
	public void create(String blogId, JsonObject post, UserInfos author,
					   final Handler<Either<String, JsonObject>> result) {
		JsonObject now = MongoDb.now();
		JsonObject blogRef = new JsonObject()
				.putString("$ref", "blogs")
				.putString("$id", blogId);
		JsonObject owner = new JsonObject()
				.putString("userId", author.getUserId())
				.putString("username", author.getUsername())
				.putString("login", author.getLogin());
		post.putObject("created", now)
				.putObject("modified", now)
				.putObject("author", owner)
				.putString("state", StateType.DRAFT.name())
				.putArray("comments", new JsonArray())
				.putNumber("views", 0)
				.putObject("blog", blogRef);
		JsonObject b = Utils.validAndGet(post, FIELDS, FIELDS);
		if (validationError(result, b)) return;
		b.putObject("sorted", now);
		if (b.containsField("content")) {
			b.putString("contentPlain",  StringUtils.stripHtmlTag(b.getString("content", "")));
		}
		mongo.save(POST_COLLECTION, b, MongoDbResult.validActionResultHandler(new Handler<Either<String,JsonObject>>() {
			public void handle(Either<String, JsonObject> event) {
				if(event.isLeft()){
					result.handle(event);
					return;
				}
				mongo.findOne(POST_COLLECTION,
					new JsonObject().putString("_id", event.right().getValue().getString("_id")),
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
					final JsonObject postFromDb = event.right().getValue().getObject("result", new JsonObject());
					final JsonObject now = MongoDb.now();
					post.putObject("modified", now);
					final JsonObject b = Utils.validAndGet(post, UPDATABLE_FIELDS, Collections.<String>emptyList());

					if (validationError(result, b)) return;
					if (b.containsField("content")) {
						b.putString("contentPlain",  StringUtils.stripHtmlTag(b.getString("content", "")));
					}

					if (postFromDb.getObject("firstPublishDate") != null) {
						b.putObject("sorted", postFromDb.getObject("firstPublishDate"));
					} else {
						b.putObject("sorted", now);
					}

					//if user is author, draft state
					if (user.getUserId().equals(postFromDb.getObject("author", new JsonObject()).getString("userId"))) {
						b.putString("state", StateType.DRAFT.name());
					}

					MongoUpdateBuilder modifier = new MongoUpdateBuilder();
					for (String attr: b.getFieldNames()) {
						modifier.set(attr, b.getValue(attr));
					}
					mongo.update(POST_COLLECTION, jQuery, modifier.build(),
							new Handler<Message<JsonObject>>() {
								@Override
								public void handle(Message<JsonObject> event) {
									if ("ok".equals(event.body().getString("status"))) {
										final JsonObject r = new JsonObject().putString("state", b.getString("state", postFromDb.getString("state")));
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

		final QueryBuilder isManagerQuery = getDefautQueryBuilder(blogId, user);
		final JsonObject sort = new JsonObject().putNumber("modified", -1);
		final JsonObject projection = defaultKeys.copy();
		projection.removeField("content");

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
						final JsonObject sortPag = new JsonObject().putNumber("sorted", -1);
						final int skip = (0 == page) ? -1 : page * limit;
						mongo.find(POST_COLLECTION, MongoQueryBuilder.build(query), sortPag, projection, skip, limit, limit, finalHandler);
					}
				}
			}
		});
	}

	@Override
	public void counter(final String blogId, final UserInfos user,
	                    final Handler<Either<String, JsonArray>> result) {
		final QueryBuilder query = QueryBuilder.start("blog.$id").is(blogId);
		final QueryBuilder isManagerQuery = getDefautQueryBuilder(blogId, user);
		final JsonObject projection = new JsonObject();
		projection.putNumber("state", 1);
		projection.putNumber("_id", -1);

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
		final JsonObject sort = new JsonObject().putNumber("modified", -1);
		final JsonObject projection = defaultKeys.copy();
		projection.removeField("content");

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
					final JsonObject sortPag = new JsonObject().putNumber("sorted", -1);
					mongo.find(POST_COLLECTION, MongoQueryBuilder.build(query), sortPag, projection, skip, limit, limit, finalHandler);
				}
			} else {
				QueryBuilder query2 = getDefautQueryBuilder(blogId, user);
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
							final JsonObject sortPag = new JsonObject().putNumber("sorted", -1);
							final int skip = (0 == page) ? -1 : page * limit;
							mongo.find(POST_COLLECTION, MongoQueryBuilder.build(listQuery), sortPag, projection, skip, limit, limit, finalHandler);
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
		final QueryBuilder isManagerQuery = getDefautQueryBuilder(blogId, user);
		final JsonObject sort = new JsonObject().putNumber("modified", -1);
		final JsonObject projection = defaultKeys.copy();
		projection.removeField("content");

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

	private QueryBuilder getDefautQueryBuilder(String blogId, UserInfos user) {
		List<DBObject> groups = new ArrayList<>();
		groups.add(QueryBuilder.start("userId").is(user.getUserId())
				.put("manager").is(true).get());
		for (String gpId: user.getProfilGroupsIds()) {
			groups.add(QueryBuilder.start("groupId").is(gpId)
					.put("manager").is(true).get());
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
		JsonObject keys = new JsonObject().putNumber("blog", 1);
		JsonArray fetch = new JsonArray().addString("blog");
		mongo.findOne(POST_COLLECTION, q, keys, fetch,
				new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				if ("ok".equals(event.body().getString("status")) &&
						event.body().getObject("result", new JsonObject()).size() > 0) {
					BlogService.PublishType type = Utils.stringToEnum(event.body().getObject("result")
							.getObject("blog",  new JsonObject()).getString("publish-type"),
							BlogService.PublishType.RESTRAINT, BlogService.PublishType.class);
					final StateType state = (BlogService.PublishType.RESTRAINT.equals(type)) ?
							StateType.SUBMITTED : StateType.PUBLISHED;
					MongoUpdateBuilder updateQuery = new MongoUpdateBuilder().set("state", state.name());

					mongo.update(POST_COLLECTION, q, updateQuery.build(), new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> res) {
							res.body().putString("state", state.name());
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
		JsonObject keys = new JsonObject().putNumber("blog", 1);
		JsonArray fetch = new JsonArray().addString("blog");
		mongo.findOne(POST_COLLECTION, q, keys, fetch, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				if ("ok".equals(event.body().getString("status")) &&
						event.body().getObject("result", new JsonObject()).size() > 0) {
					BlogService.CommentType type = Utils.stringToEnum(event.body().getObject("result")
							.getObject("blog",  new JsonObject()).getString("comment-type"),
							BlogService.CommentType.RESTRAINT, BlogService.CommentType.class);
					if (BlogService.CommentType.NONE.equals(type)) {
						result.handle(new Either.Left<String, JsonObject>("Comments are disabled for this blog."));
						return;
					}
					StateType s = BlogService.CommentType.IMMEDIATE.equals(type) ?
							StateType.PUBLISHED : StateType.SUBMITTED;
					JsonObject user = new JsonObject()
							.putString("userId", author.getUserId())
							.putString("username", author.getUsername())
							.putString("login", author.getLogin());
					JsonObject c = new JsonObject()
							.putString("comment", comment)
							.putString("id", UUID.randomUUID().toString())
							.putString("state", s.name())
							.putObject("author", user)
							.putObject("created", MongoDb.now());
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
	public void deleteComment(final String blogId, final String commentId, final UserInfos user,
			final Handler<Either<String, JsonObject>> result) {
		QueryBuilder query2 = getDefautQueryBuilder(blogId, user);
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
				JsonObject c = new JsonObject().putString("id", commentId);
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
		JsonObject keys = new JsonObject().putNumber("comments", 1).putNumber("blog", 1);
		JsonArray fetch = new JsonArray().addString("blog");
		mongo.findOne(POST_COLLECTION, MongoQueryBuilder.build(query), keys, fetch,
			new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> event) {
					JsonArray comments = new JsonArray();
					if ("ok".equals(event.body().getString("status")) &&
							event.body().getObject("result", new JsonObject()).size() > 0) {
						JsonObject res = event.body().getObject("result");
						boolean userIsManager = userIsManager(user, res.getObject("blog"));
						for (Object o: res.getArray("comments", new JsonArray())) {
							if (!(o instanceof JsonObject)) continue;
							JsonObject j = (JsonObject) o;
							if (userIsManager || StateType.PUBLISHED.name().equals(j.getString("state")) ||
									user.getUserId().equals(
											j.getObject("author", new JsonObject()).getString("userId"))) {
								comments.add(j);
							}
						}
					}
					result.handle(new Either.Right<String, JsonArray>(comments));
				}
			});
	}

	private boolean userIsManager(UserInfos user, JsonObject res) {
		if (res != null  && res.getArray("shared") != null) {
			for (Object o: res.getArray("shared")) {
				if (!(o instanceof JsonObject)) continue;
				JsonObject json = (JsonObject) o;
				return json != null && json.getBoolean("manager", false) &&
						(user.getUserId().equals(json.getString("userId")) ||
								user.getProfilGroupsIds().contains(json.getString("groupId")));
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

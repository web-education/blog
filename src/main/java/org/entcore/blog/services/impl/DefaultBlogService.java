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
import fr.wseduc.webutils.*;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultBlogService implements BlogService{

	protected static final String BLOG_COLLECTION = "blogs";

	private final MongoDb mongo;

	public DefaultBlogService(MongoDb mongo) {
		this.mongo = mongo;
	}

	@Override
	public void create(JsonObject blog, UserInfos author, final Handler<Either<String, JsonObject>> result) {
		CommentType commentType = Utils.stringToEnum(blog.getString("comment-type", "").toUpperCase(),
				CommentType.NONE, CommentType.class);
		PublishType publishType = Utils.stringToEnum(blog.getString("publish-type", "").toUpperCase(),
				PublishType.RESTRAINT, PublishType.class);
		JsonObject now = MongoDb.now();
		JsonObject owner = new JsonObject()
				.putString("userId", author.getUserId())
				.putString("username", author.getUsername())
				.putString("login", author.getLogin());
		blog.putObject("created", now)
				.putObject("modified", now)
				.putObject("author", owner)
				.putString("comment-type", commentType.name())
				.putString("publish-type", publishType.name())
				.putArray("shared", new JsonArray());
		JsonObject b = Utils.validAndGet(blog, FIELDS, FIELDS);
		if (validationError(result, b)) return;
		mongo.save(BLOG_COLLECTION, b, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> res) {
				result.handle(Utils.validResult(res));
			}
		});
	}

	@Override
	public void update(String blogId, JsonObject blog, final Handler<Either<String, JsonObject>> result) {
		blog.putObject("modified", MongoDb.now());
		if (blog.getString("comment-type") != null) {
			try {
				CommentType.valueOf(blog.getString("comment-type").toUpperCase());
				blog.putString("comment-type", blog.getString("comment-type").toUpperCase());
			} catch (IllegalArgumentException | NullPointerException e) {
				blog.removeField("comment-type");
			}
		}
		if (blog.getString("publish-type") != null) {
			try {
				PublishType.valueOf(blog.getString("publish-type").toUpperCase());
				blog.putString("publish-type", blog.getString("publish-type").toUpperCase());
			} catch (IllegalArgumentException | NullPointerException e) {
				blog.removeField("publish-type");
			}
		}
		JsonObject b = Utils.validAndGet(blog, UPDATABLE_FIELDS, Collections.<String>emptyList());
		if (validationError(result, b)) return;
		QueryBuilder query = QueryBuilder.start("_id").is(blogId);
		MongoUpdateBuilder modifier = new MongoUpdateBuilder();
		for (String attr: b.getFieldNames()) {
			modifier.set(attr, b.getValue(attr));
		}
		mongo.update(BLOG_COLLECTION, MongoQueryBuilder.build(query), modifier.build(),
				new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				result.handle(Utils.validResult(event));
			}
		});
	}

	@Override
	public void delete(final String blogId, final Handler<Either<String, JsonObject>> result) {
		QueryBuilder q = QueryBuilder.start("blog.$id").is(blogId);
		mongo.delete("posts", MongoQueryBuilder.build(q), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> res) {
				if ("ok".equals(res.body().getString("status"))) {
					QueryBuilder query = QueryBuilder.start("_id").is(blogId);
					mongo.delete(BLOG_COLLECTION, MongoQueryBuilder.build(query),
							new Handler<Message<JsonObject>>() {
								@Override
								public void handle(Message<JsonObject> event) {
									result.handle(Utils.validResult(event));
								}
							});
				} else {
					result.handle(Utils.validResult(res));
				}
			}
		});
	}

	@Override
	public void get(String blogId, final Handler<Either<String, JsonObject>> result) {
		QueryBuilder query = QueryBuilder.start("_id").is(blogId);
		mongo.findOne(BLOG_COLLECTION, MongoQueryBuilder.build(query),
				new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				result.handle(Utils.validResult(event));
			}
		});
	}

	@Override
	public void list(UserInfos user, final Integer page, final int limit, final Handler<Either<String, JsonArray>> result) {

		List<DBObject> groups = new ArrayList<>();
		groups.add(QueryBuilder.start("userId").is(user.getUserId()).get());
		for (String gpId: user.getProfilGroupsIds()) {
			groups.add(QueryBuilder.start("groupId").is(gpId).get());
		}
		QueryBuilder query = new QueryBuilder().or(
				QueryBuilder.start("author.userId").is(user.getUserId()).get(),
				QueryBuilder.start("shared").elemMatch(
				new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()
		).get());
		JsonObject sort = new JsonObject().putNumber("modified", -1);

        if (page != null) {
	        final int skip = (0 == page) ? -1 : page * limit;
	        mongo.find(BLOG_COLLECTION, MongoQueryBuilder.build(query), sort, null, skip, limit, limit,
			        new Handler<Message<JsonObject>>() {
				        @Override
				        public void handle(Message<JsonObject> event) {
					        result.handle(Utils.validResults(event));
				        }
			        });
        } else {
	        mongo.find(BLOG_COLLECTION, MongoQueryBuilder.build(query), sort, null,
			        new Handler<Message<JsonObject>>() {
				        @Override
				        public void handle(Message<JsonObject> event) {
					        result.handle(Utils.validResults(event));
				        }
			        });
        }
	}

	private boolean validationError(Handler<Either<String, JsonObject>> result, JsonObject b) {
		if (b == null) {
			result.handle(new Either.Left<String, JsonObject>("Validation error : invalids fields."));
			return true;
		}
		return false;
	}

}

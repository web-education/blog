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
import org.entcore.blog.services.PostService;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.service.impl.MongoDbSearchService;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.StringUtils;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;

public class DefaultBlogService implements BlogService{

	protected static final String BLOG_COLLECTION = "blogs";

	private final MongoDb mongo;
	private int pagingSize;
	private int searchWordMinSize;
	private final PostService postService;

	public DefaultBlogService(MongoDb mongo, PostService postService, int pagingSize, int searchWordMinSize) {
		this.mongo = mongo;
		this.pagingSize = pagingSize;
		this.postService = postService;
		this.searchWordMinSize = searchWordMinSize;
	}

	@Override
	public void create(JsonObject blog, UserInfos author, boolean isPublic, final Handler<Either<String, JsonObject>> result) {
		CommentType commentType = Utils.stringToEnum(blog.getString("comment-type", "").toUpperCase(),
				CommentType.NONE, CommentType.class);
		PublishType publishType = Utils.stringToEnum(blog.getString("publish-type", "").toUpperCase(),
				PublishType.RESTRAINT, PublishType.class);
		JsonObject now = MongoDb.now();
		JsonObject owner = new JsonObject()
				.put("userId", author.getUserId())
				.put("username", author.getUsername())
				.put("login", author.getLogin());
		blog.put("created", now)
				.put("modified", now)
				.put("author", owner)
				.put("comment-type", commentType.name())
				.put("publish-type", publishType.name())
				.put("shared", new JsonArray());
		List<String> fields = new ArrayList<>(FIELDS);
		if (isPublic) {
		    blog.put("visibility", VisibilityFilter.PUBLIC.name());
        } else {
		    blog.put("visibility", VisibilityFilter.OWNER.name());
		    blog.remove("slug");//remove field (sparse index)
			fields.remove("slug");
        }
		JsonObject b = Utils.validAndGet(blog, FIELDS, fields);
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
		blog.put("modified", MongoDb.now());
		if (blog.getString("comment-type") != null) {
			try {
				CommentType.valueOf(blog.getString("comment-type").toUpperCase());
				blog.put("comment-type", blog.getString("comment-type").toUpperCase());
			} catch (IllegalArgumentException | NullPointerException e) {
				blog.remove("comment-type");
			}
		}
		if (blog.getString("publish-type") != null) {
			try {
				PublishType.valueOf(blog.getString("publish-type").toUpperCase());
				blog.put("publish-type", blog.getString("publish-type").toUpperCase());
			} catch (IllegalArgumentException | NullPointerException e) {
				blog.remove("publish-type");
			}
		}
		//
		String visibility = blog.getString("visibility", "");
		if(!VisibilityFilter.PUBLIC.name().equals(visibility)){
			blog.remove("slug");//remove field (sparse index)
		}
		//
		JsonObject b = Utils.validAndGet(blog, UPDATABLE_FIELDS, Collections.<String>emptyList());
		if (validationError(result, b)) return;
		QueryBuilder query = QueryBuilder.start("_id").is(blogId);
		MongoUpdateBuilder modifier = new MongoUpdateBuilder();
		for (String attr: b.fieldNames()) {
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
	public void getPublic(String slug, IdType type, Handler<Either<String, JsonObject>> result) {
		QueryBuilder querySlug = QueryBuilder.start(IdType.Slug.equals(type)?"slug":"_id").is(slug);
		mongo.findOne(BLOG_COLLECTION, MongoQueryBuilder.build(querySlug),event -> {
			Either<String,JsonObject> eitherBlog = Utils.validResult(event);
			if(eitherBlog.isRight()){
				JsonObject blog = eitherBlog.right().getValue();
				postService.count(blog.getString("_id"), PostService.StateType.PUBLISHED,eventCount->{
					if(eventCount.isRight()){
						blog.put("countAll", eventCount.right().getValue());
					}
					result.handle(eitherBlog);
				});
			}else{
				result.handle(eitherBlog);
			}
		});
	}

	public void isPublicBlog(String id, IdType type, Handler<Boolean> handler){
		if (id!=null) {
			final String prop = IdType.Slug.equals(type)?"slug":"_id";
			JsonObject query = MongoQueryBuilder.build(QueryBuilder.start(prop).is(id).and("visibility").is(VisibilityFilter.PUBLIC.name()));
			mongo.count(BLOG_COLLECTION, query, event -> {
				JsonObject res = (JsonObject)event.body();
				handler.handle(res != null && "ok".equals(res.getString("status")) && 1 == res.getInteger("count"));
			});
		} else {
			handler.handle(false);
		}
	}

	public void isBlogExists(Optional<String> blogId, String slug, Handler<Boolean> handler){
		QueryBuilder queryM = QueryBuilder.start("slug").is(slug);
		if(blogId.isPresent()){
			queryM = queryM.and("_id").notEquals(blogId.get());
		}
		JsonObject query = MongoQueryBuilder.build(queryM);
		mongo.count(BLOG_COLLECTION, query, event -> {
			JsonObject res = (JsonObject)event.body();
			handler.handle(res != null && "ok".equals(res.getString("status")) && 0 != res.getInteger("count"));
		});
	}

	@Override
	public void list(UserInfos user, final Integer page, final String search, final Handler<Either<String, JsonArray>> result) {

		List<DBObject> groups = new ArrayList<>();
		groups.add(QueryBuilder.start("userId").is(user.getUserId()).get());
		for (String gpId: user.getProfilGroupsIds()) {
			groups.add(QueryBuilder.start("groupId").is(gpId).get());
		}
		QueryBuilder rightQuery = new QueryBuilder().or(
				QueryBuilder.start("author.userId").is(user.getUserId()).get(),
				QueryBuilder.start("shared").elemMatch(
				new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()
		).get());

		final QueryBuilder query;

		if (!StringUtils.isEmpty(search)) {
			final List<String> searchWords = checkAndComposeWordFromSearchText(search, this.searchWordMinSize);
			if (!searchWords.isEmpty()) {
				final QueryBuilder searchQuery = new QueryBuilder();
				searchQuery.text(MongoDbSearchService.textSearchedComposition(searchWords));
				query = new QueryBuilder().and(rightQuery.get(), searchQuery.get());
			} else {
				query = null;
				//empty result (no word to search)
				result.handle(new Either.Right<String, JsonArray>(new JsonArray()));
				return;
			}
		} else {
			query = rightQuery;
		}

		JsonObject sort = new JsonObject().put("modified", -1);

        if (page != null && query != null) {
	        final int skip = (0 == page) ? -1 : page * this.pagingSize;
	        mongo.find(BLOG_COLLECTION, MongoQueryBuilder.build(query), sort, null, skip, this.pagingSize, this.pagingSize,
			        new Handler<Message<JsonObject>>() {
				        @Override
				        public void handle(Message<JsonObject> event) {
					        result.handle(Utils.validResults(event));
				        }
			        });
        } else if (query != null) {
	        mongo.find(BLOG_COLLECTION, MongoQueryBuilder.build(query), sort, null,
			        new Handler<Message<JsonObject>>() {
				        @Override
				        public void handle(Message<JsonObject> event) {
					        result.handle(Utils.validResults(event));
				        }
			        });
        }
	}

	//TODO put this code in SearchUtils on entcore with (same code in searchengine app) and adding searchWordMinSize param
	public static List<String> checkAndComposeWordFromSearchText(final String searchText, final int searchWordMinSize) {
		final Set<String> searchWords = new HashSet<>();

		if (searchText != null) {
			//delete all useless spaces
			final String searchTextTreaty = searchText.replaceAll("\\s+", " ").trim();
			if (!searchTextTreaty.isEmpty()) {
				final List<String> words = Arrays.asList(searchTextTreaty.split(" "));
				//words search
				for (String w : words) {
					final String wTraity = w.replaceAll("(?!')\\p{Punct}", "");
					if (wTraity.length() >= searchWordMinSize) {
						searchWords.add(wTraity);
					}
				}
			}
		}
		return new ArrayList<>(searchWords);
	}

	private boolean validationError(Handler<Either<String, JsonObject>> result, JsonObject b) {
		if (b == null) {
			result.handle(new Either.Left<String, JsonObject>("Validation error : invalids fields."));
			return true;
		}
		return false;
	}

}

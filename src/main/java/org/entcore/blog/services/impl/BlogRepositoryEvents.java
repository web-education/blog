package org.entcore.blog.services.impl;

import com.mongodb.QueryBuilder;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import org.entcore.common.user.RepositoryEvents;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

public class BlogRepositoryEvents implements RepositoryEvents {

	private static final Logger log = LoggerFactory.getLogger(BlogRepositoryEvents.class);
	private final MongoDb mongo = MongoDb.getInstance();

	@Override
	public void deleteGroups(JsonArray groups) {
		for (Object o : groups) {
			if (!(o instanceof JsonObject)) continue;
			final JsonObject j = (JsonObject) o;
			final JsonObject query = MongoQueryBuilder.build(
					QueryBuilder.start("shared.groupId").is(j.getString("group")));
			JsonArray userShare = new JsonArray();
			for (Object u : j.getArray("users")) {
				JsonObject share = new JsonObject()
						.putString("userId", u.toString())
						.putBoolean("org-entcore-blog-controllers-PostController|comments", true)
						.putBoolean("org-entcore-blog-controllers-PostController|get", true)
						.putBoolean("org-entcore-blog-controllers-BlogController|get", true)
						.putBoolean("org-entcore-blog-controllers-PostController|list", true);
				userShare.addObject(share);
			}
			JsonObject update = new JsonObject()
					.putObject("$addToSet",
							new JsonObject().putObject("shared",
									new JsonObject().putArray("$each", userShare)));
			mongo.update(DefaultBlogService.BLOG_COLLECTION, query, update, false, true,
					new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> event) {
					if (!"ok".equals(event.body().getString("status"))) {
						log.error("Error updating blogs with group " +
								j.getString("group") + " : " + event.body().encode());
					} else {
						log.info("Blogs with group " + j.getString("group") +
								" updated : " + event.body().getInteger("number"));
					}
				}
			});
		}
	}

	@Override
	public void deleteUsers(JsonArray users) {
		String [] userIds = new String[users.size()];
		for (int i = 0; i < users.size(); i++) {
			JsonObject j = users.get(i);
			userIds[i] = j.getString("id");
		}
		final JsonObject query = MongoQueryBuilder.build(QueryBuilder.start("author.userId").in(userIds));
		final String collection = DefaultBlogService.BLOG_COLLECTION;
		mongo.find(collection, query, null,
				new JsonObject().putNumber("_id", 1), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> res) {
				String status = res.body().getString("status");
				JsonArray results = res.body().getArray("results");
				if ("ok".equals(status) && results != null && results.size() > 0) {
					String [] blogIds = new String[results.size()];
					for (int i = 0; i < results.size(); i++) {
						JsonObject j = results.get(i);
						blogIds[i] = j.getString("_id");
					}
					QueryBuilder q = QueryBuilder.start("blog.$id").in(blogIds);
					mongo.delete(DefaultPostService.POST_COLLECTION, MongoQueryBuilder.build(q),
							new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> event) {
							if (!"ok".equals(event.body().getString("status"))) {
								log.error("Error deleting posts : " + event.body().encode());
							} else {
								log.info("Posts deleted : " + event.body().getInteger("number"));
							}
						}
					});
					QueryBuilder query = QueryBuilder.start("_id").in(blogIds);
					mongo.delete(collection, MongoQueryBuilder.build(query),
							new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> event) {
							if (!"ok".equals(event.body().getString("status"))) {
								log.error("Error deleting blogs : " + event.body().encode());
							} else {
								log.info("Blogs deleted : " + event.body().getInteger("number"));
							}
						}
					});
				}
			}
		});
	}

}

package org.entcore.blog.services.impl;

import com.mongodb.QueryBuilder;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import org.entcore.common.service.impl.MongoDbRepositoryEvents;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public class BlogRepositoryEvents extends MongoDbRepositoryEvents {

	@Override
	public void exportResources(String externalId, String userId, JsonArray groups, String exportPath,
			String locale) {

	}

	@Override
	public void deleteUsers(JsonArray users) {
		if(users == null || users.size() == 0) {
			return;
		}

		final String[] userIds = new String[users.size()];
		for (int i = 0; i < users.size(); i++) {
			JsonObject j = users.get(i);
			userIds[i] = j.getString("id");
		}

		final JsonObject criteria = MongoQueryBuilder.build(QueryBuilder.start("shared.userId").in(userIds));

		MongoUpdateBuilder modifier = new MongoUpdateBuilder();
		modifier.pull("shared", MongoQueryBuilder.build(QueryBuilder.start("userId").in(userIds)));

		final String collection = DefaultBlogService.BLOG_COLLECTION;
		mongo.update(collection, criteria, modifier.build(), false, true, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				if (!"ok".equals(event.body().getString("status"))) {
					log.error("Error deleting users shared in collection " + collection  +
							" : " + event.body().getString("message"));
				}

				final JsonObject criteria = MongoQueryBuilder.build(QueryBuilder.start("author.userId").in(userIds));
				MongoUpdateBuilder modifier = new MongoUpdateBuilder();
				modifier.set("author.deleted", true);
				mongo.update(collection, criteria, modifier.build(), false, true,  new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> event) {
						if (!"ok".equals(event.body().getString("status"))) {
							log.error("Error deleting users shared in collection " + collection +
									" : " + event.body().getString("message"));
						} else {
							delete(collection);
						}
					}
				});
			}
		});
	}

	private void delete(final String collection) {
		final JsonObject query = MongoQueryBuilder.build(
				QueryBuilder.start("shared.org-entcore-blog-controllers-BlogController|shareJson").notEquals(true)
						.put("author.deleted").is(true));

		mongo.find(collection, query, null, new JsonObject().putNumber("_id", 1), new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> res) {
				String status = res.body().getString("status");
				JsonArray results = res.body().getArray("results");
				if ("ok".equals(status) && results != null && results.size() > 0) {
					String[] blogIds = new String[results.size()];
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

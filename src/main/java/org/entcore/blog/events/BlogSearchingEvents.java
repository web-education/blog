/*
 * Copyright © WebServices pour l'Éducation, 2014
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.blog.events;

import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Either.Right;
import org.entcore.blog.Blog;
import org.entcore.common.search.SearchingEvents;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.service.impl.MongoDbSearchService;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.entcore.common.mongodb.MongoDbResult.validResults;
import static org.entcore.common.mongodb.MongoDbResult.validResultsHandler;

public class BlogSearchingEvents implements SearchingEvents {

	private static final Logger log = LoggerFactory.getLogger(BlogSearchingEvents.class);
	private final MongoDb mongo;
	private static final String PUBLISHED_STATE = "PUBLISHED";

	public BlogSearchingEvents() {
		this.mongo = MongoDb.getInstance();
	}

	@Override
	public void searchResource(List<String> appFilters, String userId, JsonArray groupIds, final JsonArray searchWords, final Integer page, final Integer limit,
                               final JsonArray columnsHeader, final String locale, final Handler<Either<String, JsonArray>> handler) {
		if (appFilters.contains(BlogSearchingEvents.class.getSimpleName())) {

			final List<String> groupIdsLst = groupIds.toList();
			final List<DBObject> groups = new ArrayList<DBObject>();
			groups.add(QueryBuilder.start("userId").is(userId).get());
			for (String gpId: groupIdsLst) {
				groups.add(QueryBuilder.start("groupId").is(gpId).get());
			}

			final QueryBuilder rightsQuery = new QueryBuilder().or(
					QueryBuilder.start("visibility").is(VisibilityFilter.PUBLIC.name()).get(),
                    QueryBuilder.start("visibility").is(VisibilityFilter.PROTECTED.name()).get(),
                    QueryBuilder.start("visibility").is(VisibilityFilter.PROTECTED.name()).get(),
					QueryBuilder.start("author.userId").is(userId).get(),
					QueryBuilder.start("shared").elemMatch(
							new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()
					).get());

			final JsonObject projection = new JsonObject();
			projection.putNumber("_id", 1);
			//search all blogs of user
			mongo.find(Blog.BLOGS_COLLECTION, MongoQueryBuilder.build(rightsQuery), null,
					projection, new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> event) {
							final Either<String, JsonArray> ei = validResults(event);
							if (ei.isRight()) {
								final JsonArray blogsResult = ei.right().getValue();

								final Set<String> setIds = new HashSet<String>();
								for (int i=0;i<blogsResult.size();i++) {
									final JsonObject j = blogsResult.get(i);
									setIds.add(j.getString("_id"));
								}

								//search posts for the blogs found
								searchPosts(page, limit, searchWords.toList(), setIds, new Handler<Either<String, JsonArray>>() {
									@Override
									public void handle(Either<String, JsonArray> event) {
										if (event.isRight()) {
											if (log.isDebugEnabled()) {
												log.debug("[BlogSearchingEvents][searchResource] The resources searched by user are found");
											}
											final JsonArray res = formatSearchResult(event.right().getValue(), columnsHeader, searchWords.toList());
											handler.handle(new Right<String, JsonArray>(res));
										} else {
											handler.handle(new Either.Left<String, JsonArray>(event.left().getValue()));
										}
									}
								});
							} else {
								handler.handle(new Either.Left<String, JsonArray>(ei.left().getValue()));
							}
						}
					});
		} else {
			handler.handle(new Right<String, JsonArray>(new JsonArray()));
		}
	}

	private void searchPosts(int page, int limit, List<String> searchWords, final Set<String> setIds, Handler<Either<String, JsonArray>> handler) {
		final int skip = (0 == page) ? -1 : page * limit;

		final QueryBuilder worldsQuery = new QueryBuilder();
		//Set locale to "", allows to use advanced tokenization with no stemming (in fact, stemming works only with words and for a given language)
		worldsQuery.text(MongoDbSearchService.textSearchedComposition(searchWords), "");

		final QueryBuilder blogQuery = new QueryBuilder().start("blog.$id").in(setIds);
		final QueryBuilder publishedQuery = new QueryBuilder().start("state").is(PUBLISHED_STATE);

		final QueryBuilder query = new QueryBuilder().and(worldsQuery.get(), blogQuery.get(), publishedQuery.get());

		JsonObject sort = new JsonObject().putNumber("modified", -1);
		final JsonObject projection = new JsonObject();
		projection.putNumber("title", 1);
		projection.putNumber("content", 1);
		projection.putNumber("blog.$id", 1);
		projection.putNumber("modified", 1);
		projection.putNumber("author.userId", 1);
		projection.putNumber("author.username", 1);

		mongo.find(Blog.POSTS_COLLECTION, MongoQueryBuilder.build(query), sort,
				projection, skip, limit, Integer.MAX_VALUE, validResultsHandler(handler));
	}

	private JsonArray formatSearchResult(final JsonArray results, final JsonArray columnsHeader, final List<String> words) {
		final List<String> aHeader = columnsHeader.toList();
		final JsonArray traity = new JsonArray();

		for (int i=0;i<results.size();i++) {
			final JsonObject j = results.get(i);
			final JsonObject jr = new JsonObject();
			if (j != null) {
				final String blogId = j.getObject("blog").getString("$id");
				jr.putString(aHeader.get(0), j.getString("title"));
				jr.putString(aHeader.get(1), j.getString("content", ""));
				jr.putObject(aHeader.get(2), j.getObject("modified"));
				jr.putString(aHeader.get(3), j.getObject("author").getString("username"));
				jr.putString(aHeader.get(4), j.getObject("author").getString("userId"));
				jr.putString(aHeader.get(5), "/blog#/view/" + blogId + "/" + j.getString("_id"));
				traity.add(jr);
			}
		}
		return traity;
	}
}

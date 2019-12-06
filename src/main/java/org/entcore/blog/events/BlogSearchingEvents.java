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

package org.entcore.blog.events;

import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Either.Right;
import org.entcore.blog.Blog;
import org.entcore.common.search.SearchingEvents;
import org.entcore.common.service.impl.MongoDbSearchService;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

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

			final List<String> groupIdsLst = groupIds.getList();
			final List<DBObject> groups = new ArrayList<DBObject>();
			groups.add(QueryBuilder.start("userId").is(userId).get());
			for (String gpId: groupIdsLst) {
				groups.add(QueryBuilder.start("groupId").is(gpId).get());
			}

			final QueryBuilder rightsQuery = new QueryBuilder().or(
					QueryBuilder.start("author.userId").is(userId).get(),
					QueryBuilder.start("shared").elemMatch(
							new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()
					).get());

			final JsonObject projection = new JsonObject();
			projection.put("_id", 1);
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
									final JsonObject j = blogsResult.getJsonObject(i);
									setIds.add(j.getString("_id"));
								}

								//search posts for the blogs found
								searchPosts(page, limit, searchWords.getList(), setIds, new Handler<Either<String, JsonArray>>() {
									@Override
									public void handle(Either<String, JsonArray> event) {
										if (event.isRight()) {
											if (log.isDebugEnabled()) {
												log.debug("[BlogSearchingEvents][searchResource] The resources searched by user are found");
											}
											final JsonArray res = formatSearchResult(event.right().getValue(), columnsHeader, searchWords.getList());
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
		worldsQuery.text(MongoDbSearchService.textSearchedComposition(searchWords));

		final QueryBuilder blogQuery = new QueryBuilder().start("blog.$id").in(setIds);
		final QueryBuilder publishedQuery = new QueryBuilder().start("state").is(PUBLISHED_STATE);

		final QueryBuilder query = new QueryBuilder().and(worldsQuery.get(), blogQuery.get(), publishedQuery.get());

		JsonObject sort = new JsonObject().put("modified", -1);
		final JsonObject projection = new JsonObject();
		projection.put("title", 1);
		projection.put("content", 1);
		projection.put("blog.$id", 1);
		projection.put("modified", 1);
		projection.put("author.userId", 1);
		projection.put("author.username", 1);

		mongo.find(Blog.POSTS_COLLECTION, MongoQueryBuilder.build(query), sort,
				projection, skip, limit, Integer.MAX_VALUE, validResultsHandler(handler));
	}

	private JsonArray formatSearchResult(final JsonArray results, final JsonArray columnsHeader, final List<String> words) {
		final List<String> aHeader = columnsHeader.getList();
		final JsonArray traity = new JsonArray();

		for (int i=0;i<results.size();i++) {
			final JsonObject j = results.getJsonObject(i);
			final JsonObject jr = new JsonObject();
			if (j != null) {
				final String blogId = j.getJsonObject("blog").getString("$id");
				jr.put(aHeader.get(0), j.getString("title"));
				jr.put(aHeader.get(1), j.getString("content", ""));
				jr.put(aHeader.get(2), j.getJsonObject("modified"));
				jr.put(aHeader.get(3), j.getJsonObject("author").getString("username"));
				jr.put(aHeader.get(4), j.getJsonObject("author").getString("userId"));
				jr.put(aHeader.get(5), "/blog#/view/" + blogId + "/" + j.getString("_id"));
				traity.add(jr);
			}
		}
		return traity;
	}
}

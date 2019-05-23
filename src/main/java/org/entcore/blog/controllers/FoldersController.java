package org.entcore.blog.controllers;

import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.blog.security.FolderOwner;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.mongodb.MongoDbControllerHelper;
import org.vertx.java.core.http.RouteMatcher;

import java.util.Map;

public class FoldersController extends MongoDbControllerHelper {

	public FoldersController(String collectionName) {
		super(collectionName);
	}

	@Override
	public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
					 Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);
	}

	@Override
	@Get("folder/list/:filter")
	@ApiDoc("List all user folders.")
	public void list(HttpServerRequest request) {
		super.list(request);
	}

	@Post("folder")
	@ApiDoc("Add folder.")
	@SecuredAction("blog.createFolder")
	public void add(HttpServerRequest request) {
		create(request);
	}

	@Override
	@Put("folder/:id")
	@ApiDoc("Update folder by id.")
	@ResourceFilter(FolderOwner.class)
	public void update(HttpServerRequest request) {
		super.update(request);
	}

	@Override
	@Delete("folder/:id")
	@ApiDoc("Delete folder by id.")
	@ResourceFilter(FolderOwner.class)
	public void delete(HttpServerRequest request) {
		super.delete(request);
	}
}
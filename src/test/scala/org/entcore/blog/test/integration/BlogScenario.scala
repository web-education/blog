package org.entcore.blog.test.integration

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import org.entcore.test.appregistry.Role

object BlogScenario {

	val scn =
    Role.createAndSetRole("Blog")
    .exec(http("Login teacher")
      .post("""/auth/login""")
      .formParam("""email""", """${teacherLogin}""")
      .formParam("""password""", """blipblop""")
      .check(status.is(302)))
//    .exec(http("Upload blog thumbnail")
//      .post("/workspace/document?application=blog-newblog&protected=true")
//    .bodyPart(FileBodyPart("file", "tests/src/test/resources/images/blog.jpg", "image/jpeg"))
//     // .body(RawFileBody("tests/src/test/resources/images/blog.jpg"))
////      .bodyPart(RawFileBodyPart("file", "tests/src/test/resources/images/blog.jpg", "image/jpeg"))
////      .upload("file", "tests/src/test/resources/images/blog.jpg", "image/jpeg")
//      .check(status.is(201), jsonPath("$.status").is("ok"),
//        jsonPath("$._id").find.saveAs("blogImgId")))
  .exec(http("Create blog")
    .post("/blog")
	.header("Content-Type", "application/json")
	.body(StringBody("""{
		"title": "Mon premier blog",
		"description": "Le blog de la classe",
		"thumbnail": "/blog/public/img/blog.png",
		"comment-type": "NONE",
		"publish-type": "RESTRAINT"
	}""")).asJSON
    .check(status.is(200), jsonPath("$._id").find.saveAs("blogId")))
  .exec(http("Create post")
    .post("/blog/post/${blogId}")
	.header("Content-Type", "application/json")
	.body(StringBody("""{
		"title": "Le billet de l'enseignant",
		"content": "Lorem ipsum si amet dolor..."
	}""")).asJSON
    .check(status.is(200), jsonPath("$._id").find.saveAs("postId")))
  .exec(http("Create comment with comment-type: none")
    .post("/blog/comment/${blogId}/${postId}")
	.header("Content-Type", "application/json")
	.body(StringBody("""{
		"comment": "Le commentaire qui n'est pas autorisé"
	}""")).asJSON
    .check(status.is(400)))
  .exec(http("Update blog")
    .put("/blog/${blogId}")
	.header("Content-Type", "application/json")
	.body(StringBody("""{
		"comment-type": "RESTRAINT"
	}""")).asJSON
    .check(status.is(200)))
  .exec(http("Create comment with comment-type: restraint")
    .post("/blog/comment/${blogId}/${postId}")
	.header("Content-Type", "application/json")
	.body(StringBody("""{
		"comment": "Le commentaire qui est autorisé avec modération"
	}""")).asJSON
    .check(status.is(200)))
  .exec(http("List blog")
    .get("/blog/list/all")
    .check(status.is(200),
      jsonPath("$[0]._id").find.is("${blogId}")))
  .exec(http("List push before publish")
    .get("/blog/post/list/all/${blogId}")
    .check(status.is(200)))
  .exec(http("Publish post")
    .put("/blog/post/publish/${blogId}/${postId}")
    .check(status.is(200)))
  .exec(http("List post after publish")
    .get("/blog/post/list/all/${blogId}")
    .check(status.is(200)))
  .exec(http("Get share json")
    .get("/blog/share/json/${blogId}")
    .check(status.is(200)))
//  .exec(http("Share blog with students")
//    .put("/blog/share/json/${blogId}")
//    .bodyPart(StringBodyPart("groupId", "${profilGroupIds(1)}"))
//    .bodyPart(StringBodyPart("actions", "org-entcore-blog-controllers-PostController|comments"))
//    .bodyPart(StringBodyPart("actions", "org-entcore-blog-controllers-PostController|get"))
//    .bodyPart(StringBodyPart("actions", "org-entcore-blog-controllers-BlogController|get"))
//    .bodyPart(StringBodyPart("actions", "org-entcore-blog-controllers-PostController|list"))
//    .check(status.is(200)))

}

import play.api.libs.json.{Json, JsObject}
import repository.model.SessionData

val a = SessionData("aa", None, 3, "ss", false)

val b = Json.toJson(a)

b.as[JsObject]

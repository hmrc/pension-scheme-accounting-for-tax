import play.api.libs.json._

val x = Json.obj(
  "abc" -> {
    "def" -> "aaa"
  }
)

val updateReads = (__ \ 'abc \ 'xyz).json.put(JsString("aaaaaa"))

val y = x.transform(updateReads)


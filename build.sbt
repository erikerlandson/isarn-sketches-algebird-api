name := "isarn-sketches-algebird-api"

organization := "org.isarnproject"

bintrayOrganization := Some("isarn")

version := "0.0.1"

scalaVersion := "2.10.6"

crossScalaVersions := Seq("2.10.6", "2.11.8")

def commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.apache.commons" % "commons-math3" % "3.6.1",
    "org.json4s" %% "json4s-jackson" % "3.2.10",
    "org.isarnproject" %% "isarn-sketches" % "0.0.1",
    "com.twitter" %% "algebird-core" % "0.12.1",
    "org.isarnproject" %% "isarn-scalatest" % "0.0.1" % Test,
    "org.scalatest" %% "scalatest" % "2.2.4" % Test)
)

seq(commonSettings:_*)

licenses += ("Apache-2.0", url("http://opensource.org/licenses/Apache-2.0"))

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

scalacOptions in (Compile, doc) ++= Seq("-doc-root-content", baseDirectory.value+"/root-doc.txt")

site.settings

site.includeScaladoc()

// Re-enable if/when we want to support gh-pages w/ jekyll
// site.jekyllSupport()

ghpages.settings

git.remoteRepo := "git@github.com:isarn/isarn-sketches-algebird-api.git"

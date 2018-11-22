import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

lazy val root = project.in(file("."))
  .aggregate(
    coreJS,
    coreJVM,
    `interpreter-cli`,
    `interpreter-gui`,
    interpreterLogictableJS,
    interpreterLogictableJVM,
    `interpreter-play25`,
    `interpreter-play26`,
    `interpreter-js`,
    exampleProgramsJS,
    exampleProgramsJVM, 
    `sbt-uniform-parser-xsd`
  )
  .settings(
    publishLocal := {},
    publish := {},
    publishArtifact := false
  )
  .settings(commonSettings)

enablePlugins(GitVersioning)

lazy val commonSettings = Seq(
  scalaVersion := "2.12.7",
  crossScalaVersions := Seq("2.11.12", "2.12.7"),
  homepage := Some(url("https://ltbs.github.io/uniform-scala/")),
  organization := "com.luketebbs.uniform",
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3"),
  scalacOptions ++= Seq(
//    "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
    "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
    "-encoding", "utf-8",                // Specify character encoding used by source files.
    "-explaintypes",                     // Explain type errors in more detail.
    "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
    "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
    "-Xfuture",                          // Turn on future language features.
    "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
    "-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.
//    "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
    "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
    "-Xlint:option-implicit",            // Option.apply used implicit view.
    "-Xlint:package-object-classes",     // Class or object defined in package object.
    "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
    "-Xlint:unsound-match",              // Pattern match may not be typesafe.
    "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
    "-Ypartial-unification",             // Enable partial unification in type constructor inference
    "-Ywarn-dead-code",                  // Warn when dead code is identified.
//    "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
    "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen",              // Warn when numerics are widened.
    "-Ywarn-unused",
//    "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
//    "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
//   "-Ywarn-unused:locals",              // Warn if a local definition is unused.
//    "-Ywarn-unused:params",              // Warn if a value parameter is unused.
//    "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
//    "-Ywarn-unused:privates",            // Warn if a private member is unused.
    "-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
  ),
  scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings"),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/ltbs/uniform-scala"),
      "scm:git@github.com:ltbs/uniform-scala.git"
    )
  ),
  developers := List(
    Developer(
      id            = "ltbs",
      name          = "Luke Tebbs",
      email         = "luke@luketebbs.com",
      url           = url("http://www.luketebbs.com/")
    )
  ),
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishConfiguration := publishConfiguration.value.withOverwrite(isSnapshot.value),
  com.typesafe.sbt.pgp.PgpKeys.publishSignedConfiguration := com.typesafe.sbt.pgp.PgpKeys.publishSignedConfiguration.value.withOverwrite(isSnapshot.value),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(isSnapshot.value),
  com.typesafe.sbt.pgp.PgpKeys.publishLocalSignedConfiguration := com.typesafe.sbt.pgp.PgpKeys.publishLocalSignedConfiguration.value.withOverwrite(isSnapshot.value),
  git.gitTagToVersionNumber := { tag: String =>
    if(tag matches "[0-9]+\\..*") Some(tag)
    else None
  },
  useGpg := true,
  licenses += ("GPL-3", url("https://www.gnu.org/licenses/gpl-3.0.en.html"))
)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
    scalaVersion := "2.12.7",
    crossScalaVersions := Seq("2.11.12", "2.12.7"),
    libraryDependencies += "org.atnos" %%% "eff" % "5.2.0",
    scalaJSUseMainModuleInitializer := true
  )

lazy val coreJS = core.js
lazy val coreJVM = core.jvm

lazy val `interpreter-cli` = project
  .settings(commonSettings)
  .settings(
    scalaVersion := "2.12.7",
    crossScalaVersions := Seq("2.11.12", "2.12.7")
  )
  .dependsOn(coreJVM)

lazy val `interpreter-gui` = project
  .settings(commonSettings)
  .settings(
    scalaVersion := "2.12.7",
    crossScalaVersions := Seq("2.11.12", "2.12.7")
  )
  .dependsOn(coreJVM)

lazy val `interpreter-logictable` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
    scalaVersion := "2.12.7",
    crossScalaVersions := Seq("2.11.12", "2.12.7")
  )

lazy val interpreterLogictableJS = `interpreter-logictable`.js
  .dependsOn(coreJS)
  .dependsOn(exampleProgramsJS % "test")
lazy val interpreterLogictableJVM = `interpreter-logictable`.jvm
  .dependsOn(coreJVM)
  .dependsOn(exampleProgramsJVM % "test")

lazy val prototype = project
  .settings(commonSettings)
  .settings(
    scalaVersion := "2.12.7",
    crossScalaVersions := Seq("2.11.12", "2.12.7")
  )
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "org.querki" %%% "jquery-facade" % "1.2"
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJS,exampleProgramsJS)

lazy val html = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings)
  .settings(
    scalaVersion := "2.12.7",
    crossScalaVersions := Seq("2.11.12", "2.12.7")
  )
  .settings(libraryDependencies ++= Seq(
              "com.lihaoyi" %%% "scalatags" % "0.6.7",
              "com.chuusai" %%% "shapeless" % "2.3.3"))

lazy val htmlJS = html.js.dependsOn(coreJS)
lazy val htmlJVM = html.jvm.dependsOn(coreJVM)

lazy val `interpreter-play`: sbtcrossproject.CrossProject = crossProject(Play25, Play26)
  .crossType(CrossType.Full)
  .settings(commonSettings)

lazy val `interpreter-play25` = `interpreter-play`.projects(Play25)
  .dependsOn(coreJVM)
  .settings(
    name := "interpreter-play25",
    scalaVersion := "2.11.12",
    crossScalaVersions := Seq("2.11.12")
  )

lazy val `interpreter-play26` = `interpreter-play`.projects(Play26)
  .dependsOn(coreJVM)
  .settings(
    name := "interpreter-play26"
  )

lazy val `interpreter-js` = project
  .settings(commonSettings)
  .settings(
    scalaVersion := "2.12.7",
    crossScalaVersions := Seq("2.11.12", "2.12.7")
  )
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "org.querki" %%% "jquery-facade" % "1.2"
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJS)


lazy val `gforms-parser` = crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.5" % "test",
    libraryDependencies += "com.github.pureconfig" %% "pureconfig" % "0.9.2" % "compile",
    libraryDependencies += "com.github.pureconfig" %% "pureconfig-enumeratum" % "0.9.2" % "compile",
    libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1" % "compile",
    libraryDependencies += "com.beachape" %% "enumeratum" % "1.5.13"
  )

lazy val gformsParserJS = `gforms-parser`.js.dependsOn(coreJS)
lazy val gformsParserJVM = `gforms-parser`.jvm.dependsOn(coreJVM)

lazy val `sbt-uniform-parser-xsd` = project.settings(commonSettings)
  .enablePlugins(SbtPlugin)
  .settings(crossScalaVersions := Seq(scalaVersion.value))
  .dependsOn(coreJVM)

lazy val `ofsted-uipack` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)

lazy val ofstedUiPackJS = `ofsted-uipack`.js.dependsOn(coreJS)
lazy val ofstedUiPackJVM = `ofsted-uipack`.jvm.dependsOn(coreJVM)

lazy val `ofsted-program` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(libraryDependencies += "com.beachape" %%% "enumeratum" % "1.5.13")

lazy val `ofsted-prototype` = project.settings(commonSettings)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "org.querki" %%% "jquery-facade" % "1.2"
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJS)
  .dependsOn(gformsParserJS)
  .dependsOn(prototype)
  .dependsOn(ofstedProgramJS)

lazy val `example-programs` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
    scalaVersion := "2.12.7",
    crossScalaVersions := Seq("2.11.12", "2.12.7")
  )

lazy val exampleProgramsJS = `example-programs`.js.dependsOn(coreJS)
lazy val exampleProgramsJVM = `example-programs`.jvm.dependsOn(coreJVM)

lazy val `example-play` = project.settings(commonSettings)
  .enablePlugins(PlayScala)
  .dependsOn(coreJVM, `interpreter-play26`, exampleProgramsJVM)
  .dependsOn(interpreterLogictableJVM % "test")
  .settings(
    libraryDependencies ++= Seq(filters,guice)
  )

lazy val `example-js` = project
  .settings(commonSettings)
  .settings(
    scalaVersion := "2.12.7",
    crossScalaVersions := Seq("2.11.12", "2.12.7")
  )
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "org.querki" %%% "jquery-facade" % "1.2"
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJS, `interpreter-js`, exampleProgramsJS)

lazy val `ofsted-play` = project
  .dependsOn(`interpreter-play26`, ofstedProgramJVM)
  .settings(commonSettings)
  .enablePlugins(PlayScala)
  .settings(
    scalaVersion := "2.12.7",
    crossScalaVersions := Seq("2.12.7"),
    libraryDependencies ++= Seq(filters,guice)
  )

lazy val ofstedProgramJS = `ofsted-program`.js.dependsOn(gformsParserJS)
lazy val ofstedProgramJVM = `ofsted-program`.jvm.dependsOn(gformsParserJVM)

lazy val docs = project
  .dependsOn(coreJVM, `interpreter-play26`, interpreterLogictableJVM, `interpreter-cli`, exampleProgramsJVM)
  .enablePlugins(MicrositesPlugin)
  .settings(commonSettings)
  .settings(
    scalaVersion := "2.12.7",
    fork in Test := true,
    micrositeName           := "uniform-scala",
    micrositeDescription    := "Purely functional user-interaction",
    micrositeAuthor         := "Luke Tebbs",
    micrositeGithubOwner    := "ltbs",
    micrositeGithubRepo     := "uniform-scala",
    micrositeBaseUrl        := "/uniform-scala",
    micrositeHighlightTheme := "color-brewer",
    micrositeConfigYaml     := microsites.ConfigYml(yamlCustomProperties = Map(
      "last-stable-version" -> com.typesafe.sbt.SbtGit.GitKeys.gitDescribedVersion.value.fold("")(_.takeWhile(_ != '-'))
    )),
    micrositePalette := Map(
      "brand-primary"   -> "#5236E0",
      "brand-secondary" -> "#32423F",
      "brand-tertiary"  -> "#232F2D",
      "gray-dark"       -> "#3E4645",
      "gray"            -> "#7F8483",
      "gray-light"      -> "#E2E3E3",
      "gray-lighter"    -> "#F3F4F4",
      "white-color"     -> "#FFFFFF"),
    micrositeExtraMdFiles := Map(),
    scalacOptions in Tut --= Seq("-Ywarn-unused-import", "-Ywarn-unused:imports", "-Ywarn-unused"),
//    scalacOptions in Tut += "-Xfatal-warnings", // play controller scuppers this
    libraryDependencies += "com.typesafe.play" %% "play" % "2.6.20" // used for the play interpreter demo
  )

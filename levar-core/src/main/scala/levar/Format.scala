package levar

import org.joda.time._
import org.joda.time.format.DateTimeFormat

object Format {
  def datefmt(d: DateTime): String =
    DateTimeFormat.forPattern("yyyy-MM-dd").print(d.withZone(DateTimeZone.getDefault))

  def datetimefmt(d: DateTime): String =
    DateTimeFormat.forPattern("yyyy-MM-dd HH:mm").print(d.withZone(DateTimeZone.getDefault))

  def datatsetRSnames(datasetRS: ResultSet[Dataset]): String = {
    datasetRS.items.map(_.id).map { x => s"- $x" }.mkString("\n")
  }

  def datasetRStoString(datasetRS: ResultSet[Dataset]): String = {
    if (datasetRS.nonEmpty) {
      val sb = new StringBuilder()
      sb ++=
        """|Dataset                              | Updated    | Type       |    Items
           |-------------------------------------|------------|------------|----------""".stripMargin
      for (dataset <- datasetRS) {
        sb ++= "\n"
        val displayName = if (dataset.id.size > 36) {
          dataset.id.take(33) + "..."
        } else {
          dataset.id + (" " * (36 - dataset.id.size))
        }
        val date = dataset.updatedAt match {
          case Some(d) => datefmt(d)
          case None => " " * 10
        }
        val dtype = {
          val t = dataset.dtype match {
            case Dataset.ClassificationType => "classify"
            case Dataset.RegressionType => "regression"
          }
          t + (" " * (10 - t.size))
        }
        val n = dataset.size.getOrElse(0)
        sb ++= f"$displayName | $date | $dtype | $n%8d"
      }
      sb.toString
    } else {
      s"No datasets"
    }
  }

  def datumRStoString(datumRS: ResultSet[Datum]): String = {
    // TODO change
    s"Reults: ${datumRS.total.getOrElse(datumRS.items.size)}"
  }

  private def fieldfmt(field: String, n: Int = 20) = {
    if (field.size > n) {
      field.take(n - 3) + "..."
    } else {
      field ++ (" " * (n - field.size))
    }
  }

  def commentToString(comment: Comment): String = {
    val cdatefmt = comment.createdAt.map(datetimefmt).getOrElse("no date")
    val commentfmt = comment.comment.replaceAll("\n", "  \n")
    s"""
    |- ${comment.username}
    |  $cdatefmt
    |  $commentfmt""".stripMargin

  }

  def datasetToString(dataset: Dataset): String = {
    val sb = new StringBuilder()
    sb ++= s"Dataset: ${dataset.id}"
    sb ++= s"\nType:    ${dataset.dtype.name}"
    sb ++= s"\nItems:   ${dataset.size.map(_.toString).getOrElse("N/A")}"
    sb ++= s"\nCreated: ${dataset.createdAt.map(datetimefmt).getOrElse("N/A")}"
    sb ++= s"\nUpdated: ${dataset.updatedAt.map(datetimefmt).getOrElse("N/A")}"
    sb ++= s"\nSchema:"
    for ((field, fieldType) <- dataset.schema.fields) {
      sb ++= f"\n- ${fieldfmt(field)} ${fieldType.name}"
    }
    for (classCounts <- dataset.classCounts) {
      sb ++= "\nClasses:"
      for ((cls, count) <- classCounts) {
        sb ++= f"\n- ${fieldfmt(cls)} $count"
      }
    }
    for (summaryStats <- dataset.summaryStats) {
      sb ++= f"""
      |Score distribution:
      |- min value            ${summaryStats.minVal}%.2f
      |- max value            ${summaryStats.maxVal}%.2f
      |- mean                 ${summaryStats.mean}%.2f
      |- stddev               ${summaryStats.stddev}%.2f
      |- median               ${summaryStats.median}%.2f
      |- 10th percentile      ${summaryStats.p10}%.2f
      |- 90th percentile      ${summaryStats.p90}%.2f""".stripMargin
    }
    for (tags <- dataset.labels) {
      sb ++= s"\nTags: ${tags.mkString(",")}"
    }
    for (comments <- dataset.comments) {
      sb ++= "\nComments"
      for (comment <- comments) {
        sb ++= "\n"
        sb ++= commentToString(comment)
      }
    }
    sb.toString
  }

  def experimentRStoString(rs: ResultSet[Experiment]) = {
    if (rs.noResults) {
      "No matching experiments"
    } else {
      val exps = for (exp <- rs.items) yield { s"- ${exp.id}" }
      exps.mkString("\n")
    }
  }

  def experimentToString(experiment: Experiment) = {

    val sb = new StringBuilder()
    sb ++= s"Experiment:    ${experiment.displayName}"

    experiment.datasetType foreach { dtype =>
      sb ++= s"\nType:          ${dtype.name}"
    }

    experiment.size foreach { num =>
      sb ++= s"\nPredictions:   $num"
      experiment.datasetSize foreach { dsSize =>
        if (dsSize == num) {
          sb ++= " (full dataset coverage)"
        } else {
          sb ++= s" (of $dsSize)"
        }
      }
    }

    experiment.classificationResults foreach { results =>
      sb ++= "\n\nResults:\n"

      sb ++= f"\nOverall accuracy: ${results.overallAccuracy}%.3f\n"

      val clsWidth = (Seq(10) ++ results.classes.map(_.size)).max + 1

      sb ++= "\n Class"
      sb ++= " " * (clsWidth - "Class".size)
      sb ++= " | Prec | Rec  |  F1\n"
      sb ++= "-" * (clsWidth + 2)
      sb ++= "|------|------|------"
      for (cls <- results.classes) {
        sb ++= "\n "
        sb ++= cls
        sb ++= " " * (clsWidth - cls.size)
        val p = f"${100 * results.precision(cls)}%.1f"
        val r = f"${100 * results.recall(cls)}%.1f"
        val f1 = f"${100 * results.f1(cls)}%.1f"
        val pp = " " * (4 - p.size) + p
        val rr = " " * (4 - r.size) + r
        val ff1 = " " * (4 - f1.size) + f1
        sb ++= s" | $pp | $rr | $ff1"
      }
      sb ++= "\n"

      sb ++= "\nConfusion matrix (columns give predicted counts; rows give gold counts)\n"

      sb ++= "\n Class"
      sb ++= " " * (clsWidth - "Class".size)
      for (cls <- results.classes) {
        sb ++= " |"
        sb ++= " " * (clsWidth - cls.size)
        sb ++= cls
      }
      sb ++= " |     Totals"
      sb ++= "\n-"
      sb ++= "-" * (clsWidth + 1)
      for (cls <- results.classes) {
        sb ++= "|"
        sb ++= "-" * (clsWidth + 1)
      }
      sb ++= "|------------"

      for (gcls <- results.classes) {
        sb ++= "\n "
        sb ++= gcls
        sb ++= " " * (clsWidth - gcls.size)
        for (pcls <- results.classes) {
          sb ++= " |"
          val num = results.num(gcls, pcls).toString
          sb ++= " " * (clsWidth - num.size)
          sb ++= num
        }
        sb ++= " | "
        val goldTotal = results.goldSum(gcls).toString
        sb ++= " " * (10 - goldTotal.size)
        sb ++= goldTotal
      }
      sb ++= "\n Totals"
      sb ++= " " * (clsWidth - "Totals".size)
      for (cls <- results.classes) {
        sb ++= " |"
        val num = results.predSum(cls).toString
        sb ++= " " * (clsWidth - num.size)
        sb ++= num
      }
      sb ++= " | "
      val num = results.total.toString
      sb ++= " " * (10 - num.size)
      sb ++= num
    }

    sb.toString
  }
}

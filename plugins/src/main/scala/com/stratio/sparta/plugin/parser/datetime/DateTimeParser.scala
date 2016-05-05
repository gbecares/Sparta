/*
 * Copyright (C) 2015 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stratio.sparta.plugin.parser.datetime

import java.io.{Serializable => JSerializable}
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import com.stratio.sparta.sdk.{Parser, TypeOp}
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.StructType
import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}

class DateTimeParser(order: Integer,
                     inputField: String,
                     outputFields: Seq[String],
                     schema: StructType,
                     properties: Map[String, JSerializable])
  extends Parser(order, inputField, outputFields, schema, properties) {

  val formats = properties.get("inputFormat")

  //scalastyle:off
  override def parse(row: Row, removeRaw: Boolean): Row = {
    val inputValue = row.get(inputFieldIndex)
    val newData = {
      val formatterOption = DateTimeParser.formatter(formats)
      outputFields.map(outputField => {
        val outputValue = outputFieldsSchema.find(field => field.name == outputField)
        outputValue match {
          case Some(outValue) =>
            if (formatterOption.isDefined && !inputValue.isInstanceOf[Date]) {
              formatterOption.get match {
                case Right("unix") =>
                  TypeOp.transformValueByTypeOp(outValue.dataType,
                    (inputValue.toString.toLong * 1000L).asInstanceOf[Any])
                case Right("unixMillis") =>
                  TypeOp.transformValueByTypeOp(outValue.dataType,
                    inputValue.toString.toLong.asInstanceOf[Any])
                case Right("autoGenerated") =>
                  TypeOp.transformValueByTypeOp(outValue.dataType, new Date().getTime.asInstanceOf[Any])
                case Right("hive") =>
                  TypeOp.transformValueByTypeOp(
                    outValue.dataType, getDateFromHiveFormat(inputValue.toString).getTime).asInstanceOf[Any]
                case Left(formatter) =>
                  TypeOp.transformValueByTypeOp(
                    outValue.dataType, formatter.parseDateTime(inputValue.toString).toDate.getTime).asInstanceOf[Any]
              }
            } else TypeOp.transformValueByTypeOp(outValue.dataType, inputValue)
          case None =>
            throw new IllegalStateException(s"Impossible to parse inputField: $inputField in the schema.")
        }
      })
    }
    val prevData = if (removeRaw) row.toSeq.drop(1) else row.toSeq

    Row.fromSeq(prevData ++ newData)
  }

  protected def getDateFromHiveFormat(hiveFormatDate: String): Date = {
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    sdf.parse(hiveFormatDate)
  }
}

object DateTimeParser {

  val formatMethods = classOf[ISODateTimeFormat].getMethods.toSeq.map(x => (x.getName, x)).toMap

  def formatter(formats: Option[JSerializable]): Option[Either[DateTimeFormatter, String]] = {
    formats match {
      case Some(format) => {
        format.toString match {
          case "unix" => Some(Right("unix"))
          case "unixMillis" => Some(Right("unixMillis"))
          case "autoGenerated" => Some(Right("autoGenerated"))
          case "hive" => Some(Right("hive"))
          case _ => Some(Left(formatMethods(format.toString).invoke(None).asInstanceOf[DateTimeFormatter]))
        }
      }
      case None => None
    }
  }
}

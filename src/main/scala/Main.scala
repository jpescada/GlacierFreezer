package com.mathieubolla.glacierfreezer

import scala.collection.JavaConversions._
import org.apache.commons.io.IOUtils
import java.io.{ByteArrayInputStream, InputStream, FileInputStream, FileOutputStream, File}

import com.amazonaws.{AmazonClientException, AmazonServiceException}
import com.amazonaws.auth.{AWSCredentials, PropertiesCredentials}
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.glacier.{AmazonGlacierClient, TreeHashGenerator}
import com.amazonaws.services.glacier.model.{DescribeVaultRequest, UploadArchiveRequest, JobParameters, InitiateJobRequest}
import com.amazonaws.util.BinaryUtils;

import java.security.DigestInputStream;
import java.security.MessageDigest;

import com.mathieubolla.glacierfreezer.Utils._

import scala.io.Source

object Glacier {
	def main(args:Array[String]) {
		lazy val cache = {
			val CacheLine = "(.*)[\t](.*)[\t](.*)".r
			val Failure = "(.*)[\t](.*)[\t]failed".r
			val cache = new scala.collection.mutable.HashMap[String, String]
			for (line <- Source.fromFile("cache.dat").getLines()) {
				line match {
					case Failure(path, sha1) => Unit
					case CacheLine(path, sha1, archiveId) => {
						cache.put(sha1, archiveId)
					}
					case _ => Console.println("No decoder for " + line + ". Your cache.dat file seems to be corrupted.")
				}
			}
			cache
		}

		val results = walkFiles(new File(getUserInput("Photo path to upload: ", "/tmp/photo")))
			.filter(regularFile => regularFile.getCanonicalPath().endsWith(".CR2"))
			.map(regularFile => (regularFile, computeSha1(regularFile)))
			.filter(pairOfPathAndSha1 => !cache.contains(pairOfPathAndSha1._2))
			.map(pairOfPathAndSha1 => Services.freeze(pairOfPathAndSha1))

		results.filter(a => a._3.isDefined).map(a => appendToFile("cache.dat", a._1 + "\t" + a._2 + "\t" + a._3.get.getArchiveId()))
		results.filter(a => !a._3.isDefined).map(a => appendToFile("cache.dat", a._1 + "\t" + a._2 +"\tfailed"))

		//Services.dumpInventory(Services.prepareInventory(configFor("vault.name")))
	}	
}
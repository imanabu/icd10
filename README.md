# icd10
ICD-10 Lookup Engine API
========================

This project started out as an way to provide REST API for our own internal use to look up
ICD-10 code, which became officially effective on October 1, 2015 in the United States.

Current Status
==============

We have built a web site "WinguMD ICD-10 Web Site" as a demonstration of our engine and also as
as the basis for testing. We also are using this as our PR tool, but with that we do not
prohibit you from doing the same on your own.

On this release the REST API is not yet available but the demonstration of the engine is
in place and widely requesting people to provide inputs to support the actual usecases.

The Road-Map
===========

We plan to maintain this as a public REST API site, which, anyone is welcome to perform quires.

How We Built It
================

* We are using the XML file from the CDC web site at http://www.cdc.gov/nchs/icd/icd10cm.htm
* We then built a very simple web page using the TypeSafe Activator platform in Java 8 and Scala.

Is There Support? How Can I Contact the Developer?
==================================================

* It is a open source software, you can download it and play with it and do anything with it, but generally there is not free support.
* You can contact me at Manabu<at>Wingumd.com for non product support inquiries.








The cadcUWS library provides the code necessary to implement a Universal Worker Service (UWS)
asynchronous job execution resource. It also provides a simple way to re-use the class that
implements the "job" via a synchronous resource.

**Steps to implement a UWS service**

1. Implement a JobRunner that performs the application logic: the job execution.

2. Implement a custom JobManager, probably by subclassing SimpleJobManager and setting up
the dependencies.

3. Configure async and/or sync servlet resources to use the custom JobManager.

Dependencies:

Servlet --> JobManager --> JobPersistence
                       --> JobExecutor --> JobUpdater
                                       --> JobRunner --> JobUpdater

**Primary Interfaces**

1. JobManager - the main entry-point for the library

The JobManager is the interface between the HTTP layer and the other cadcUWS components. In general, 
a JobManager delegates to other components to do most of the work, but it does provide some policy 
(default values for some UWS job control values, authorization, etc). There is one provided 
implementation: SimpleJobManager. To use this class, one simply has to configure it with JobPersistence 
and JobExecutor implementations. This is easiest to do by creating a subclass with a no-arg constructor 
and instantiating usable implementations of other components there. See the SampleJobManager in cadcSampleUWS.

2. JobPersistence - this component stores jobs so they can be retrieved in multiple requests

There are two provided implementations for JobPersistence. MemoryJobPersistence uses an in-memory map to
store jobs and is suitable for testing only as it has no facility for removing old jobs and the map would
currently grow without bound. DatabaseJobPersistence (and the JobDAO class) uses a RDBMS to store and 
retrieve jobs; it must currently be subclassed to provide methods that find or create a DataSource and 
that find/load/create a JobSchema instance to describe the database details. There are sample DDLs for 
tables (Sybase ASQ and PostgreSQL) that work with DatabasePersistence/JobDAO combination and test classes 
for each. The test classes show how to setup a suitable JobSchema to go with the tables. The build file currently 
executes the Sybase tests as part of the test target so that will fail without some setup.

  Additional implementation dependencies:

  MemoryPersistence --> StringIDGenerator (default: RandomStringGenerator(16))
                    --> IdentityManager (default: X500IdentityManager)

  DatabaseJobPersistence --> StringIDGenerator (default: RandomStringGenerator(16))
                         --> IdentityManager (default: X500IdentityManager)
                         --> JobDAO

3. JobExecutor - this component manages the execution  (and abort) of both sync and async jobs

There is one implementation of JobExecutor. ThreadExecutor creates a new Thread for each job. A ThreadPoolExecutor 
will be implemented in the near future.

4. JobRunner - the JobRunner interface defines the code that actually implements the job itself

There is no provided implementation of the JobRunner since this is custom code for each different service. See
the cadcSampleUWS (HelloWorld) or cadcTAP (QueryRunner) modules for working JobRunner implementations.

In general, the provided implementations have constructors and setter methods for any components they depend on; the
plan is to make these suitable for other configuration mehcanisms (e.g. dependency injection).

**The HTTP layer**

The HTTP layer intracts with the rest of the library through the JobManager interface. Each 
UWS resource (async and sync) must be configured with the name of the class that implements 
this interface (currently: support for alternate configuration mechanisms is TBD).

The async part of the library is implemented as a restlet (www.restlet.org) application. This
is deployed using a standard servlet provided by the restlet library. In the web.xml:
```
    <servlet>
        <load-on-startup>2</load-on-startup>
        <servlet-name>AsyncServlet</servlet-name>
        <servlet-class>org.restlet.ext.servlet.ServerServlet</servlet-class>
        <init-param>
            <param-name>org.restlet.application</param-name>
            <param-value>ca.nrc.cadc.uws.web.restlet.UWSAsyncApplication</param-value>
        </init-param>
        <init-param>
          <param-name>ca.nrc.cadc.uws.server.JobManager</param-name>
          <param-value>ca.nrc.cadc.uws.sample.SampleJobManager</param-value>
        </init-param>
    </servlet>

    <servlet-mapping>
            <servlet-name>AsyncServlet</servlet-name>
            <url-pattern>/async/*</url-pattern>
    </servlet-mapping>
```
The sync resource is implemented as a plain old Java servlet:
```
    <servlet>
        <load-on-startup>2</load-on-startup>
      <servlet-name>SyncServlet</servlet-name>
      <servlet-class>ca.nrc.cadc.uws.server.SyncServlet</servlet-class>
      <init-param>
          <param-name>ca.nrc.cadc.uws.server.JobManager</param-name>
          <param-value>ca.nrc.cadc.uws.sample.SampleJobManager</param-value>
      </init-param>
      <init-param>
          <param-name>ca.nrc.cadc.uws.server.SyncServlet.execOnGET</param-name>
          <param-value>true</param-value>
      </init-param>
      <init-param>
          <param-name>ca.nrc.cadc.uws.server.SyncServlet.execOnPOST</param-name>
          <param-value>false</param-value>
      </init-param>
    </servlet>

    <servlet-mapping>
            <servlet-name>SyncServlet</servlet-name>
            <url-pattern>/sync/*</url-pattern>
    </servlet-mapping>
```
    Note: these above example web.xml deployments are from the cadcSampleUWS/src/web.xml and refer to
    JobManager class in that project.

For the SyncServlet, there are two additional init-params that control the behaviour of the servlet.
When these options are disabled (default: false) the SyncServlet will respond to a job-creation 
request (GET or POST) with a redirect to /sync/(job-id)/run and the job will execute and return results
when the client performs a GET on that URL. When enabled, the SyncServlet will immediately execute the
job in the initial request. In general, execOnGET is safe and RESTful. However, execOnPOST is not really 
RESTful  as a POST should modify buit not return a representation of the resource. Using execOnPOST=false 
follows the standard Post-Redirect-Get pattern). However, execOnPOST could be RESTful if the JobRunner itself
is written to issue a redirect to another resource where the result is obtained.


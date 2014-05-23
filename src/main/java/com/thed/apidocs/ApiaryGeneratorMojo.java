package com.thed.apidocs;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;


public class ApiaryGeneratorMojo extends AbstractMojo {
    Logger logger = Logger.getLogger(ApiaryGeneratorMojo.class.getName());
    final String packageName = "com.thed.service.rest.resource";
    final String vmFile = "apiary.vm";

    /**
     * Location of the file.
     * @parameter expression="${project.build.directory}"
     * @required
     */
    final String outputFileName = "target/apiary.apib";


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.getProperties().put("catalina.home", ".");	// for log4j
		BasicConfigurator.configure();
        ApiaryGeneratorMojo gen = new ApiaryGeneratorMojo();
        try {
            gen.execute();
        } catch (MojoExecutionException e) {
            e.printStackTrace();
        } catch (MojoFailureException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                        .setUrls(ClasspathHelper.forJavaClassPath())
        );
        Set<Class<?>> types = reflections.getTypesAnnotatedWith(Path.class);
        Ordering<Class> order = new Ordering<Class>() {
            @Override
            public int compare(Class left, Class right) {
                String leftName = StringUtils.substringAfterLast(left.getName(), ".");
                String rightName = StringUtils.substringAfterLast(right.getName(), ".");
                return leftName.compareTo(rightName);
            }
        };
        List<Class<?>> sortedTypes = order.sortedCopy(Iterables.filter(types, new Predicate<Class<?>>() {
            @Override
            public boolean apply(Class<?> input) {
                return input.getName().startsWith(packageName);
            }
        }));

        List<Resource> list = new ArrayList<ApiaryGeneratorMojo.Resource>();
        for(Class type : sortedTypes){
            if(type.getName().startsWith(packageName) && type.isInterface()){
                System.out.println(type);
                list.add(getResourceMetadata(type));
            }
        }
        generateDocs(list);
    }

	/* Sample annotations -
	@Service("zephyrTestcase")
	@Path("/testcase")
	@Produces({MediaType.APPLICATION_JSON , MediaType.APPLICATION_XML})
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Api(value = "/testcase", description = "get testcase by id and criteria")
	*/
	private Resource getResourceMetadata(Class clazz) {
		Resource r = new Resource();

		Annotation[] ann = clazz.getAnnotations() ;

		Service service = (Service)clazz.getAnnotation(Service.class);
//		Service s = (Service) service ;
		Path path = (Path) clazz.getAnnotation(Path.class);
		Produces produces = (Produces) clazz.getAnnotation(Produces.class);
		Consumes consumes = (Consumes) clazz.getAnnotation(Consumes.class);
		Api api = (Api) clazz.getAnnotation(Api.class);

		r.setName(extractResourcePrefix(clazz.getName()));
		r.setGroupNotes(api.description());
		r.setPath(supressDuplicateSlash(path.value()));
		r.setProduces(StringUtils.join(produces.value(), " "));
		r.setConsumes(StringUtils.join(consumes.value(), " "));

		for (Method m: clazz.getMethods())  {
			getOperationMetadata(r, m);
		}
		return r ;
	}

	private String extractResourcePrefix(String s) {
		String[] sa = StringUtils.split(s, ".");
		String resourceName = sa[sa.length-1];
		String name = resourceName.substring(0, resourceName.indexOf("Resource"));
		return name ;
	}

	/*
	@GET
	@Path("/{id}")
	@ApiOperation(value = "Get testcase by ID", //notes = "Add extra notes here",
					responseClass = "com.thed.rpc.bean.RemoteRepositoryTreeTestcase")
	@ApiErrors(value = { @ApiError(code = 400, reason = "Invalid ID supplied"),
							@ApiError(code = 404, reason = "Testcase not found") })
	*/
	private void getOperationMetadata(Resource r, Method m) {
		Operation op = new Operation();
		if (m.getAnnotation(GET.class) != null) {
			op.setRequestType("GET");
		} else if (m.getAnnotation(POST.class) != null) {
			op.setRequestType("POST");
		} else if (m.getAnnotation(PUT.class) != null) {
			op.setRequestType("PUT");
		}

		Path path = (Path) m.getAnnotation(Path.class);
		op.setPath(supressDuplicateSlash(r.getPath() + "/" + path.value()));

		ApiOperation api = (ApiOperation) m.getAnnotation(ApiOperation.class);
		if (api != null) {
			op.setName(api.value());
			op.setDescription(api.value());	// don't have description, duplicating name value
		} else {
			op.setName("TODO: please add description");
			op.setDescription("TODO: please add description");
		}

		// use Resource's annotation if required
		if (m.getAnnotation(Produces.class) != null) {
			Produces produces = (Produces) m.getAnnotation(Produces.class);
			op.setProduces(StringUtils.join(produces.value(), " "));
		} else {
			op.setProduces(r.getProduces());
		}

		if (m.getAnnotation(Consumes.class) != null) {
			Consumes consumes = (Consumes) m.getAnnotation(Consumes.class);
			op.setConsumes(StringUtils.join(consumes.value(), " "));
		} else {
			op.setConsumes(r.getConsumes());
		}

		if (r.getOperations() == null) {
			r.setOperations(new ArrayList<ApiaryGeneratorMojo.Operation>());
		}
		r.getOperations().add(op);
		op.setJsonRequest(new ArrayList<String>());
		op.setJsonResponse(new ArrayList<String>());
		op.setResponseCode("200");

		getUrlParameter(r, op, m);
	}

	private void getUrlParameter(Resource r, Operation op, Method m) {
		Annotation[][] pa = m.getParameterAnnotations() ;
//		System.out.println(pa);

		/* E.g. AttachmentResource  */
		/*
		public List<RemoteAttachment> getAttachments(
				@ApiParam(value = "Id of entity which need to be fetched", required = true)
				@QueryParam("entityid") String entityId,
				@ApiParam(value = "Entity name, possible values : testcase, requirement, testStepResult, releaseTestSchedule")
				@QueryParam("entityname") String entityName,
				@ApiParam(value = "Token stored in cookie, fetched automatically if available", required = false)
				@CookieParam("token") Cookie tokenFromCookie) throws ZephyrServiceException;
		*/
		Class[] params = m.getParameterTypes() ;
//		TypeVariable<Method>[] tvm = m.getTypeParameters();
		for (int i = 0; i < pa.length; i++) {
			Annotation[] eachParam = pa[i] ;
			// ignore ApiParam or PathParam or CookieParam ignore
			QueryParam qpAnnotation = hasQueryParam(eachParam) ;
			if (qpAnnotation != null) {

				if (op.getQueryParams() == null) {
					List<QueryParameter> queryParams = new ArrayList<ApiaryGeneratorMojo.QueryParameter>();
					op.setQueryParams(queryParams);
				}
				
				QueryParameter qParam = new QueryParameter();
				qParam.setName(qpAnnotation.value());
				qParam.setType(params[i].getSimpleName());
				qParam.setDescription(getApiDescription(eachParam));
				op.getQueryParams().add(qParam);
			}
		}
		
	}
	
	private QueryParam hasQueryParam(Annotation[] paramAnnotaions) {
		for (Annotation ax: paramAnnotaions) {
			if (ax instanceof QueryParam) {
				return (QueryParam) ax ;
			}
		}
		return null ;
	}
	
	private String getApiDescription(Annotation[] paramAnnotaions) {
		for (Annotation ax: paramAnnotaions) {
			if (ax instanceof ApiParam) {
				return ((ApiParam) ax).value() ;
			}
		}
		return "TODO: please provide a description" ;
	}
	
	private String supressDuplicateSlash(String s) {
		String rep = s.replaceAll("//", "/");
		return rep ;
	}
	
	/* prototyping 
	private List<Resource> gatherData() {
		List<Resource> resources = new ArrayList<Resource>();
		Resource r = new Resource();
		r.setName("Testcase");
		r.setGroupNotes("Create a testcase");
		
		List<Operation> ops = new ArrayList<Operation>();
		Operation op = new Operation();
		op.setName("create");
		op.setDescription("Some description");
		op.setRequestType("POST");
		op.setPath("/testcase");
		op.setProduces("application/json");
		
		List<String> jsonResponse = new ArrayList<String>();
		jsonResponse.add("[{");
		jsonResponse.add("\"id\": 1, \"title\": \"Test..... in park\"");
		jsonResponse.add	("}]");		
		op.setJsonResponse(jsonResponse);
		op.setResponseCode("200");
		ops.add(op);
		
		r.setOperations(ops);
		resources.add(r);
		return resources ;
	}
	*/

    /**
     *
     * @param resources
     */
	private void generateDocs(List<Resource> resources) {
		Velocity.init();
		VelocityContext context = new VelocityContext();
		context.put("name", new String("Velocity"));
		Template template = null;
		
		context.put("resources", resources);
		context.put("DOUBLE_HASH", "##");
		context.put("TRIPLE_HASH", "###");
        PrintWriter pw = null;
		try {
            VelocityEngine ve = new VelocityEngine();
            ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
            ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
			template = ve.getTemplate(vmFile);
			StringWriter sw = new StringWriter();
			template.merge(context, sw);
			pw = new PrintWriter(new File(outputFileName));
            pw.write(sw.toString());
            pw.flush();
            logger.fine("Log file is generated " + outputFileName);
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
            pw.close();
        }
	}

    /**
     *
     */
    public class Resource {
		private String name ;
		private String groupNotes ;
		private String path ;
		private String produces ;
		private String consumes ;
		private List<Operation> operations ;
		
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public String getGroupNotes() {
			return groupNotes;
		}
		public void setGroupNotes(String groupNotes) {
			this.groupNotes = groupNotes;
		}
		public List<Operation> getOperations() {
			return operations;
		}
		public void setOperations(List<Operation> operations) {
			this.operations = operations;
		}
		public String getPath() {
			return path;
		}
		public void setPath(String path) {
			this.path = path;
		}
		public String getProduces() {
			return produces;
		}
		public void setProduces(String produces) {
			this.produces = produces;
		}
		public String getConsumes() {
			return consumes;
		}
		public void setConsumes(String consumes) {
			this.consumes = consumes;
		}
		
	}
	
	public class Operation {
		private String name ;
		private String description ;
		private String path ;
		/** POST, GET, etc */
		private String requestType ;
		
		// e.g. "application/json". If multiple values, exist, provide a final value to be put.
		private String consumes ;
		
		/** Line by line text of request json */
		private List<String> jsonRequest ;

		// e.g. "application/json". If multiple values, exist, provide a final value to be put.
		private String produces ;
		
		/** Line by line text of response json */
		private List<String> jsonResponse ;
		/** e.g. 200 */
		private String responseCode ;
		
		private List<QueryParameter> queryParams ;		
		
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public String getDescription() {
			return description;
		}
		public void setDescription(String description) {
			this.description = description;
		}
		public String getPath() {
			return path;
		}
		public void setPath(String path) {
			this.path = path;
		}
		public String getRequestType() {
			return requestType;
		}
		public void setRequestType(String requestType) {
			this.requestType = requestType;
		}
		public List<String> getJsonRequest() {
			return jsonRequest;
		}
		public void setJsonRequest(List<String> jsonRequest) {
			this.jsonRequest = jsonRequest;
		}
		public List<String> getJsonResponse() {
			return jsonResponse;
		}
		public void setJsonResponse(List<String> jsonResponse) {
			this.jsonResponse = jsonResponse;
		}
		public String getResponseCode() {
			return responseCode;
		}
		public void setResponseCode(String responseCode) {
			this.responseCode = responseCode;
		}
		public String getConsumes() {
			return consumes;
		}
		public void setConsumes(String consumes) {
			this.consumes = consumes;
		}
		public String getProduces() {
			return produces;
		}
		public void setProduces(String produces) {
			this.produces = produces;
		}
		public List<QueryParameter> getQueryParams() {
			return queryParams;
		}
		public void setQueryParams(List<QueryParameter> queryParams) {
			this.queryParams = queryParams;
		}
		
	}
	
	public class QueryParameter {
		private String name ;
		private String type ;
		private String description ;
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public String getType() {
			return type;
		}
		public void setType(String type) {
			this.type = type;
		}
		public String getDescription() {
			return description;
		}
		public void setDescription(String description) {
			this.description = description;
		}
	}

}

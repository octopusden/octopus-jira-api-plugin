package org.octopusden.octopus.jira.api.rest;

import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.octopusden.octopus.jira.api.dto.IPSRequest;
import org.octopusden.octopus.jira.api.exception.BadRequestException;
import org.octopusden.octopus.jira.api.exception.FailedGenerateIPSException;
import org.octopusden.octopus.jira.api.service.IPSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

@AnonymousAllowed
@Path("/api")
public class ApiRestController {

    private static final Logger logger = LoggerFactory.getLogger(ApiRestController.class);
    private static final DateTimeFormatter DATE_FORMAT_YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter DATE_FORMAT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final IPSService ipsService;
    private final ObjectMapper objectMapper;

    public ApiRestController(IPSService ipsService, ObjectMapper objectMapper) {
        this.ipsService = ipsService;
        this.objectMapper = objectMapper;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("ips/{ips}")
    public Response getIPS(
            @PathParam("ips") String ips,
            @QueryParam("since") Integer sinceYear,
            @QueryParam("sinceDate") String sinceDate,
            @QueryParam("release") String release,
            @QueryParam("system") @DefaultValue("CLASSIC") String system,
            @QueryParam("mandatory") @DefaultValue("true") boolean mandatory
    ) {
        IPSRequest request;
        if (release != null) {
            request = new IPSRequest(ips, release, null, system, mandatory);
        } else {
            Date startDate;
            try {
                if (sinceYear != null) {
                    startDate = Date.from(
                            Year.parse(String.valueOf(sinceYear), DATE_FORMAT_YEAR)
                                    .atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                    );
                } else if (sinceDate != null) {
                    startDate = Date.from(
                            LocalDate.parse(sinceDate, DATE_FORMAT_DATE)
                                    .atStartOfDay().toInstant(ZoneOffset.UTC)
                    );
                } else {
                    throw new BadRequestException("Either since or sinceDate parameters need to be set");
                }
            } catch (DateTimeParseException e) {
                throw new BadRequestException("Invalid date format: " + e.getMessage());
            }
            request = new IPSRequest(ips, null, startDate, system, mandatory);
        }

        try {
            Object result = ipsService.generate(request);
            logger.info("Generated IPS data for {}:{}", request.getIps(), request.getRelease());
            return Response.ok(objectMapper.writeValueAsString(result)).build();
        } catch (IllegalArgumentException e) {
            String msg = "Bad request generating IPS data for " + request.getIps() + ":" + request.getRelease()
                    + " — " + (e.getMessage() != null ? e.getMessage() : "unknown error");
            logger.warn(msg);
            throw new BadRequestException(msg);
        } catch (Exception e) {
            String msg = "Failed to generate IPS data for " + request.getIps() + ":" + request.getRelease() + " " + e;
            logger.error(msg);
            throw new FailedGenerateIPSException(msg);
        }
    }
}

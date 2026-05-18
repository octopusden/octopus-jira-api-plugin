package org.octopusden.octopus.jira.api.rest

import com.atlassian.plugins.rest.common.security.AnonymousAllowed
import com.fasterxml.jackson.databind.ObjectMapper
import org.octopusden.octopus.jira.api.service.IPSRequest
import org.octopusden.octopus.jira.api.service.IPSService
import java.net.URLDecoder
import java.text.SimpleDateFormat
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import org.slf4j.LoggerFactory

@AnonymousAllowed
@Path("/api")
class ApiRestService(
    private val ipsService: IPSService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ApiRestService::class.java)
        private val DATE_FORMAT_YEAR = SimpleDateFormat("yyyy")
        private val DATE_FORMAT_DATE = SimpleDateFormat("yyyy-MM-dd")
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("ips/{ips}/type/{type}")
    fun getIPS(
        @PathParam("ips") ips: String,
        @PathParam("type") type: String,
        @QueryParam("since") sinceYear: Int?,
        @QueryParam("sinceDate") sinceDate: String?,
        @QueryParam("release") release: String?,
        @QueryParam("system") @DefaultValue("CLASSIC") system: String,
        @QueryParam("clientCode") clientCode: String?,
        @QueryParam("mandatory") @DefaultValue("true") mandatory: Boolean
    ): Response {
        val request = if (release != null) {
            IPSRequest(
                ips = URLDecoder.decode(ips, "UTF-8"),
                ipsType = type,
                release = release,
                startDate = null,
                system = system,
                clientCode = clientCode,
                mandatory = mandatory
            )
        } else {
            val startDate = when {
                sinceYear != null -> DATE_FORMAT_YEAR.parse(sinceYear.toString())
                sinceDate != null -> DATE_FORMAT_DATE.parse(sinceDate)
                else -> return Response.status(400)
                    .entity("""{"error":"either since or sinceDate parameters need to be set"}""")
                    .build()
            }
            IPSRequest(
                ips = URLDecoder.decode(ips, "UTF-8"),
                ipsType = type,
                release = null,
                startDate = startDate,
                system = system,
                clientCode = clientCode,
                mandatory = mandatory
            )
        }

        return try {
            val result = ipsService.generate(request)
            logger.info("Generated IPS data for ${request.ips}:${request.release}")
            Response.ok(objectMapper.writeValueAsString(result)).build()
        } catch (e: IllegalArgumentException) {
            logger.warn("Bad request generating IPS data for ${request.ips}:${request.release} — ${e.message}")
            Response.status(400).entity("""{"error":"${e.message}"}""").build()
        } catch (e: Exception) {
            logger.error("Failed to generate IPS data for ${request.ips}:${request.release}", e)
            Response.serverError().entity("""{"error":"${e.message}"}""").build()
        }
    }
}

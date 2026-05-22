package org.octopusden.octopus.jira.api.rest

import com.atlassian.plugins.rest.common.security.AnonymousAllowed
import com.fasterxml.jackson.databind.ObjectMapper
import org.octopusden.octopus.jira.api.dto.IPSRequest
import org.octopusden.octopus.jira.api.exception.BadRequestException
import org.octopusden.octopus.jira.api.exception.FailedGenerateIPSException
import org.octopusden.octopus.jira.api.service.IPSService
import java.net.URLDecoder
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.time.Year

@AnonymousAllowed
@Path("/api")
class ApiRestController(
    private val ipsService: IPSService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ApiRestController::class.java)
        private val DATE_FORMAT_YEAR = DateTimeFormatter.ofPattern("yyyy")
        private val DATE_FORMAT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("ips/{ips}")
    fun getIPS(
        @PathParam("ips") ips: String,
        @QueryParam("since") sinceYear: Int?,
        @QueryParam("sinceDate") sinceDate: String?,
        @QueryParam("release") release: String?,
        @QueryParam("system") @DefaultValue("CLASSIC") system: String,
        @QueryParam("mandatory") @DefaultValue("true") mandatory: Boolean
    ): Response {
        val request = if (release != null) {
            IPSRequest(
                ips = URLDecoder.decode(ips, "UTF-8"),
                release = release,
                startDate = null,
                system = system,
                mandatory = mandatory
            )
        } else {
            val startDate = try {
                when {
                    sinceYear != null -> Date.from(
                        Year.parse(sinceYear.toString(), DATE_FORMAT_YEAR)
                            .atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                    )
                    sinceDate != null -> Date.from(
                        LocalDate.parse(sinceDate, DATE_FORMAT_DATE)
                            .atStartOfDay().toInstant(ZoneOffset.UTC)
                    )
                    else -> throw BadRequestException("Either since or sinceDate parameters need to be set")
                }
            } catch (e: DateTimeParseException) {
                throw BadRequestException("Invalid date format: ${e.message}")
            }
            IPSRequest(
                ips = URLDecoder.decode(ips, "UTF-8"),
                release = null,
                startDate = startDate,
                system = system,
                mandatory = mandatory
            )
        }

        return try {
            val result = ipsService.generate(request)
            logger.info("Generated IPS data for ${request.ips}:${request.release}")
            Response.ok(objectMapper.writeValueAsString(result)).build()
        } catch (e: IllegalArgumentException) {
            "Bad request generating IPS data for ${request.ips}:${request.release} — ${e.message ?: "unknown error"}".let {
                logger.warn(it)
                throw BadRequestException(it)
            }
        } catch (e: Exception) {
            "Failed to generate IPS data for ${request.ips}:${request.release} ${e}".let {
                logger.error(it)
                throw FailedGenerateIPSException(it)
            }
        }
    }
}

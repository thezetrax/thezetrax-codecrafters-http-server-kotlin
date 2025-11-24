package http.types

import http.models.HttpRequest
import http.models.HttpResponse

typealias HttpHandler = (HttpRequest, HttpResponse) -> HttpResponse

package serwio.visit.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class VisitConflictException extends ErrorResponseException {
    VisitConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, problem(code, message), null);
    }

    private static ProblemDetail problem(String code, String message) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message);
        problem.setProperty("code", code);
        return problem;
    }
}

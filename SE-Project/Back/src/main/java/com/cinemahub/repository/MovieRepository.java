package com.cinemahub.repository;

import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findByApprovalStatus(ApprovalStatus approvalStatus);
}

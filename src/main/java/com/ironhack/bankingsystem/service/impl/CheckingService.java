package com.ironhack.bankingsystem.service.impl;

import com.ironhack.bankingsystem.model.Checking;
import com.ironhack.bankingsystem.model.StudentChecking;
import com.ironhack.bankingsystem.repository.CheckingRepository;
import com.ironhack.bankingsystem.repository.StudentCheckingRepository;
import com.ironhack.bankingsystem.service.interfaces.ICheckingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class CheckingService implements ICheckingService {

    @Autowired
    private CheckingRepository checkingRepository;

    @Autowired
    private StudentCheckingRepository studentCheckingRepository;

    public Checking createChecking(Checking checking) {
        if(checking.getBalance().getAmount().compareTo(BigDecimal.valueOf(0)) < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The balance must be greater or equals than 0");
        } else {
            if(ChronoUnit.YEARS.between(checking.getPrimaryOwner().getBirthDate(), LocalDateTime.now()) > 24) {
                return checkingRepository.save(checking);
            } else {
                StudentChecking studentChecking = new StudentChecking(checking.getBalance(),
                        checking.getPrimaryOwner(), checking.getSecondaryOwner(), checking.getSecretKey());
                studentCheckingRepository.save(studentChecking);
                return null;
            }

        }
    }
}

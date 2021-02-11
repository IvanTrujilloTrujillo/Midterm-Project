package com.ironhack.bankingsystem.service.impl;

import com.ironhack.bankingsystem.classes.Money;
import com.ironhack.bankingsystem.controller.dtos.BalanceDTO;
import com.ironhack.bankingsystem.controller.dtos.MoneyDTO;
import com.ironhack.bankingsystem.enums.Status;
import com.ironhack.bankingsystem.model.Account;
import com.ironhack.bankingsystem.model.Saving;
import com.ironhack.bankingsystem.model.Transaction;
import com.ironhack.bankingsystem.repository.*;
import com.ironhack.bankingsystem.service.interfaces.IAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;

@Service
public class AccountService implements IAccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CheckingRepository checkingRepository;

    @Autowired
    private StudentCheckingRepository studentCheckingRepository;

    @Autowired
    private SavingRepository savingRepository;

    @Autowired
    private CreditCardRepository creditCardRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ThirdPartyRepository thirdPartyRepository;

    public Money getAccountBalance(Long id) {
        if(accountRepository.existsById(id)) {
            return accountRepository.findById(id).get().getBalance();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The Account Id doesn't exist");
        }
    }

    public void setAccountBalance(Long id, BalanceDTO balance) {
        if(accountRepository.existsById(id)) {
            Account account = accountRepository.findById(id).get();
            try {
                Money newBalance = new Money(balance.getAmount(), Currency.getInstance(balance.getCurrency()));
                account.setBalance(newBalance);
                accountRepository.save(account);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "It isn't a supported ISO 4217 code");
            }

        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The Account Id doesn't exist");
        }
    }

    public Money getBalanceForAccount(Long id, UserDetails userDetails) {
        if(accountRepository.existsById(id)) {

            Status status = Status.ACTIVE;
            if(checkingRepository.existsById(id)){
                status = checkingRepository.findById(id).get().getStatus();
            } else if (studentCheckingRepository.existsById(id)){
                status = studentCheckingRepository.findById(id).get().getStatus();
            } else if (savingRepository.existsById(id)) {
                status = savingRepository.findById(id).get().getStatus();
            }

            if(accountRepository.findById(id).get().getPrimaryOwner().getUsername().equals(userDetails.getUsername()) ||
                    (accountRepository.findById(id).get().getSecondaryOwner() != null &&
                     accountRepository.findById(id).get().getSecondaryOwner().getUsername().equals(userDetails.getUsername()))) {
                if(status == Status.ACTIVE) {
                    if (checkingRepository.existsById(id)) {
                        if (checkingRepository.findById(id).get().getBalance().getAmount().compareTo(
                                checkingRepository.findById(id).get().getMinimumBalance().getAmount()) < 0) {

                            checkingRepository.findById(id).get().getBalance().decreaseAmount(
                                    checkingRepository.findById(id).get().getPenaltyFee());
                            checkingRepository.save(checkingRepository.findById(id).get());

                        }
                    } else if (savingRepository.existsById(id)) {
                        if (ChronoUnit.YEARS.between(savingRepository.findById(id).get().getLastInterestAddedDate(), LocalDateTime.now()) > 1) {

                            BigDecimal amount = savingRepository.findById(id).get().getBalance().getAmount();
                            amount = amount.add(amount.multiply(savingRepository.findById(id).get().getInterestRate()));
                            savingRepository.findById(id).get().setBalance(
                                    new Money(amount, savingRepository.findById(id).get().getBalance().getCurrency()));

                            savingRepository.findById(id).get().setLastInterestAddedDate(LocalDateTime.now());

                            savingRepository.save(savingRepository.findById(id).get());

                        }

                        if (savingRepository.findById(id).get().getBalance().getAmount().compareTo(
                                savingRepository.findById(id).get().getMinimumBalance().getAmount()) < 0) {

                            savingRepository.findById(id).get().getBalance().decreaseAmount(
                                    savingRepository.findById(id).get().getPenaltyFee());
                            savingRepository.save(savingRepository.findById(id).get());

                        }
                    } else if (creditCardRepository.existsById(id)) {
                        if (ChronoUnit.MONTHS.between(creditCardRepository.findById(id).get().getLastInterestAddedDate(), LocalDateTime.now()) > 1) {

                            BigDecimal amount = creditCardRepository.findById(id).get().getBalance().getAmount();
                            amount = amount.add(amount.multiply(
                                    creditCardRepository.findById(id).get().getInterestRate().divide(BigDecimal.valueOf(12))));
                            creditCardRepository.findById(id).get().setBalance(
                                    new Money(amount, creditCardRepository.findById(id).get().getBalance().getCurrency()));

                            creditCardRepository.findById(id).get().setLastInterestAddedDate(LocalDateTime.now());

                            creditCardRepository.save(creditCardRepository.findById(id).get());

                        }
                    }

                    return accountRepository.findById(id).get().getBalance();
                } else {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account is frozen. Contact with an admin");
                }
            }
            else {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don´t have access to this account");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The Account Id doesn't exist");
        }
    }

    public void transferMoney(Transaction transaction, UserDetails userDetails) {
        Account senderAccount;

        if (accountRepository.existsById(transaction.getSenderAccount().getId())) {
            senderAccount = accountRepository.findById(transaction.getSenderAccount().getId()).get();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The sender account doesn't exist");
        }
        Long receiverAccountId = transaction.getReceiverAccountId();

        if(senderAccount.getPrimaryOwner().getUsername().equals(userDetails.getUsername()) ||
                (senderAccount.getSecondaryOwner() != null &&
                 senderAccount.getSecondaryOwner().getUsername().equals(userDetails.getUsername()))) {

            Status status = Status.ACTIVE;
            if(checkingRepository.existsById(senderAccount.getId())){
                status = checkingRepository.findById(senderAccount.getId()).get().getStatus();
            } else if (studentCheckingRepository.existsById(senderAccount.getId())){
                status = studentCheckingRepository.findById(senderAccount.getId()).get().getStatus();
            } else if (savingRepository.existsById(senderAccount.getId())) {
                status = savingRepository.findById(senderAccount.getId()).get().getStatus();
            }

            if(status == Status.ACTIVE) {

                LocalDateTime lastTransaction;
                if(transactionRepository.findLastTransactionBySenderAccount(senderAccount).isPresent()) {
                    lastTransaction = transactionRepository.findLastTransactionBySenderAccount(senderAccount).get();
                } else {
                    lastTransaction = LocalDateTime.MIN;
                }

                if(ChronoUnit.SECONDS.between(lastTransaction, LocalDateTime.now()) > 0) {

                    //Take the transactions made on the last 24 hours
                    List<Transaction> lastTwentyFourHoursTransactions = transactionRepository.
                            findByTransactionDateBetweenAndSenderAccount(
                            LocalDateTime.now().minusHours(24), LocalDateTime.now(), senderAccount);
                    BigDecimal sumLastTransactions = BigDecimal.valueOf(0);

                    //If there are at least one, add the amount of each one
                    if(lastTwentyFourHoursTransactions.size() > 0) {
                        for (Transaction transactionFor : lastTwentyFourHoursTransactions) {
                            sumLastTransactions = sumLastTransactions.add(transactionFor.getAmount().getAmount());
                        }
                    }
                    //And add this one
                    sumLastTransactions = sumLastTransactions.add(transaction.getAmount().getAmount());

                    //If this sum is less than a 150% of the account's limit or the limit is 0, it can continue
                    if(sumLastTransactions.compareTo(
                            senderAccount.getMaxLimitTransactions().getAmount().multiply(
                                    BigDecimal.valueOf(1.5))) < 1 ||
                            senderAccount.getMaxLimitTransactions().getAmount().compareTo(
                                    BigDecimal.valueOf(0)) == 0) {

                        //If the limit is 0, it means there are very few transactions
                        if(senderAccount.getMaxLimitTransactions().getAmount().compareTo(
                                BigDecimal.valueOf(0)) == 0) {

                            //Take the transactions made before last 24 hours
                            List<Transaction> transactionsBeforeTwentyFourHour = transactionRepository.
                                    findByTransactionDateBetweenAndSenderAccount(
                                    LocalDateTime.MIN, LocalDateTime.now().minusHours(24), senderAccount);
                            //If there are some transactions in before this period, we want to sum the amount of these
                            //and set the limit with this sum
                            if(transactionsBeforeTwentyFourHour.size() > 0) {

                                BigDecimal sumTransactionsBeforeLastTwentyFourHours = BigDecimal.valueOf(0);
                                for (Transaction transactionFor : transactionsBeforeTwentyFourHour) {
                                    sumTransactionsBeforeLastTwentyFourHours = sumTransactionsBeforeLastTwentyFourHours
                                            .add(transactionFor.getAmount().getAmount());
                                }

                                //As we still don't have the limit on the account, we must compare if the transaction is legal
                                if(sumLastTransactions.compareTo(
                                        sumTransactionsBeforeLastTwentyFourHours.multiply(BigDecimal.valueOf(1.5))) > 0) {

                                    if(checkingRepository.existsById(senderAccount.getId())){
                                        checkingRepository.findById(senderAccount.getId()).get().setStatus(Status.FROZEN);
                                        checkingRepository.save(checkingRepository.findById(senderAccount.getId()).get());
                                    } else if (studentCheckingRepository.existsById(senderAccount.getId())){
                                        studentCheckingRepository.findById(senderAccount.getId()).get().setStatus(Status.FROZEN);
                                        studentCheckingRepository.save(studentCheckingRepository.findById(senderAccount.getId()).get());
                                    } else if (savingRepository.existsById(senderAccount.getId())) {
                                        savingRepository.findById(senderAccount.getId()).get().setStatus(Status.FROZEN);
                                        savingRepository.save(savingRepository.findById(senderAccount.getId()).get());
                                    }

                                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have tried to make a " +
                                            "transaction above your limit in the last 24 hours, your account would be " +
                                            "frozen. Contact with an admin");
                                }

                                senderAccount.setMaxLimitTransactions(new Money(
                                        sumTransactionsBeforeLastTwentyFourHours, senderAccount.getBalance().getCurrency()));
                            }
                        //Here we know the sum is less than a 150%, but, if is greater than the limit, we must set the limit
                        } else if(sumLastTransactions.compareTo(
                                senderAccount.getMaxLimitTransactions().getAmount()) > 0) {

                            senderAccount.setMaxLimitTransactions(new Money(
                                    sumLastTransactions, senderAccount.getBalance().getCurrency()));

                        }

                        if (transaction.getAmount().getAmount().compareTo(
                                senderAccount.getBalance().getAmount()) < 0) {
                            if (accountRepository.existsById(receiverAccountId)) {
                                if (accountRepository.findById(receiverAccountId).get().getPrimaryOwner().getName().equals(
                                        transaction.getReceiverAccountHolderName()) ||
                                        (accountRepository.findById(receiverAccountId).get().getSecondaryOwner() != null &&
                                                accountRepository.findById(receiverAccountId).get().getSecondaryOwner().getName().equals(
                                                        transaction.getReceiverAccountHolderName()))) {

                                    Status status2 = Status.ACTIVE;
                                    if(checkingRepository.existsById(receiverAccountId)){
                                        status2 = checkingRepository.findById(receiverAccountId).get().getStatus();
                                    } else if (studentCheckingRepository.existsById(receiverAccountId)){
                                        status2 = studentCheckingRepository.findById(receiverAccountId).get().getStatus();
                                    } else if (savingRepository.existsById(receiverAccountId)) {
                                        status2 = savingRepository.findById(receiverAccountId).get().getStatus();
                                    }

                                    if(status2 == Status.ACTIVE) {

                                        senderAccount.getBalance().decreaseAmount(transaction.getAmount());
                                        accountRepository.save(senderAccount);

                                        Account receiverAccount = accountRepository.findById(receiverAccountId).get();
                                        receiverAccount.getBalance().increaseAmount(transaction.getAmount());
                                        accountRepository.save(receiverAccount);

                                        transaction.setTransactionDate(LocalDateTime.now());
                                        transactionRepository.save(transaction);

                                    } else {
                                        throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The receiver account is frozen");
                                    }

                                } else {
                                    throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The receiver name doesn't match " +
                                            "with the owners of the Account Id");
                                }
                            } else {
                                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The Account Id of the receiver " +
                                        "doesn't exist");
                            }
                        } else {
                            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "You don´t have enough balance");
                        }


                    } else {
                        if(checkingRepository.existsById(senderAccount.getId())){
                            checkingRepository.findById(senderAccount.getId()).get().setStatus(Status.FROZEN);
                            checkingRepository.save(checkingRepository.findById(senderAccount.getId()).get());
                        } else if (studentCheckingRepository.existsById(senderAccount.getId())){
                            studentCheckingRepository.findById(senderAccount.getId()).get().setStatus(Status.FROZEN);
                            studentCheckingRepository.save(studentCheckingRepository.findById(senderAccount.getId()).get());
                        } else if (savingRepository.existsById(senderAccount.getId())) {
                            savingRepository.findById(senderAccount.getId()).get().setStatus(Status.FROZEN);
                            savingRepository.save(savingRepository.findById(senderAccount.getId()).get());
                        }

                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have tried to make a transaction " +
                                "above your limit in the last 24 hours, your account would be frozen. Contact with an admin");
                    }
                } else {
                    if(checkingRepository.existsById(senderAccount.getId())){
                        checkingRepository.findById(senderAccount.getId()).get().setStatus(Status.FROZEN);
                        checkingRepository.save(checkingRepository.findById(senderAccount.getId()).get());
                    } else if (studentCheckingRepository.existsById(senderAccount.getId())){
                        studentCheckingRepository.findById(senderAccount.getId()).get().setStatus(Status.FROZEN);
                        studentCheckingRepository.save(studentCheckingRepository.findById(senderAccount.getId()).get());
                    } else if (savingRepository.existsById(senderAccount.getId())) {
                        savingRepository.findById(senderAccount.getId()).get().setStatus(Status.FROZEN);
                        savingRepository.save(savingRepository.findById(senderAccount.getId()).get());
                    }


                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have tried to make two transaction " +
                            "in 1 second, your account would be frozen. Contact with an admin");
                }
            } else {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account is frozen. Contact with an admin");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don´t have access to the sender account");
        }
    }

    public void receiveMoney(Long id, String secretKey, MoneyDTO amount, String hashedKey, UserDetails userDetails) {
        if(accountRepository.existsById(id)) {
            if(thirdPartyRepository.findByHashedKey(hashedKey).isPresent()) {
                if(userDetails.getUsername().equals(thirdPartyRepository.findByHashedKey(hashedKey).get().getUsername())) {

                    Status status = Status.ACTIVE;
                    if(checkingRepository.existsById(id)){
                        status = checkingRepository.findById(id).get().getStatus();
                    } else if (studentCheckingRepository.existsById(id)){
                        status = studentCheckingRepository.findById(id).get().getStatus();
                    } else if (savingRepository.existsById(id)) {
                        status = savingRepository.findById(id).get().getStatus();
                    }

                    if(status == Status.ACTIVE) {
                        if (accountRepository.findById(id).get().getBalance().getAmount().compareTo(amount.getAmount()) > 0) {
                            if (checkingRepository.existsById(id)) {
                                if (checkingRepository.findById(id).get().getSecretKey().equals(secretKey)) {

                                    Account account = accountRepository.findById(id).get();
                                    account.getBalance().decreaseAmount(amount.getAmount());
                                    accountRepository.save(account);

                                } else {
                                    throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The Secret Key doesn't match " +
                                            "with the Account");
                                }
                            } else if (studentCheckingRepository.existsById(id)) {
                                if (studentCheckingRepository.findById(id).get().getSecretKey().equals(secretKey)) {

                                    Account account = accountRepository.findById(id).get();
                                    account.getBalance().decreaseAmount(amount.getAmount());
                                    accountRepository.save(account);

                                } else {
                                    throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The Secret Key doesn't match " +
                                            "with the Account");
                                }
                            } else if (savingRepository.existsById(id)) {
                                if (savingRepository.findById(id).get().getSecretKey().equals(secretKey)) {

                                    Account account = accountRepository.findById(id).get();
                                    account.getBalance().decreaseAmount(amount.getAmount());
                                    accountRepository.save(account);

                                } else {
                                    throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The Secret Key doesn't match " +
                                            "with the Account");
                                }
                            } else {
                                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "You can't receive from a Credit Card");
                            }
                        } else {
                            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Not enough balance");
                        }
                    } else {
                        throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The sender's account is frozen");
                    }
                } else {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don´t have access to the Third Party account");
                }
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "It doesn't exist any Third Party with this hashed key");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The Account Id of the sender doesn't exist");
        }
    }

    public void sendMoney(Long id, String secretKey, MoneyDTO amount, String hashedKey, UserDetails userDetails) {
        if(accountRepository.existsById(id)) {
            if(thirdPartyRepository.findByHashedKey(hashedKey).isPresent()) {
                if(userDetails.getUsername().equals(thirdPartyRepository.findByHashedKey(hashedKey).get().getUsername())) {

                    Status status = Status.ACTIVE;
                    if(checkingRepository.existsById(id)){
                        status = checkingRepository.findById(id).get().getStatus();
                    } else if (studentCheckingRepository.existsById(id)){
                        status = studentCheckingRepository.findById(id).get().getStatus();
                    } else if (savingRepository.existsById(id)) {
                        status = savingRepository.findById(id).get().getStatus();
                    }

                    if(status == Status.ACTIVE) {
                        if (checkingRepository.existsById(id)) {
                            if (checkingRepository.findById(id).get().getSecretKey().equals(secretKey)) {

                                Account account = accountRepository.findById(id).get();
                                account.getBalance().increaseAmount(amount.getAmount());
                                accountRepository.save(account);

                            } else {
                                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The Secret Key doesn't match " +
                                        "with the Account");
                            }
                        } else if (studentCheckingRepository.existsById(id)) {
                            if (studentCheckingRepository.findById(id).get().getSecretKey().equals(secretKey)) {

                                Account account = accountRepository.findById(id).get();
                                account.getBalance().increaseAmount(amount.getAmount());
                                accountRepository.save(account);

                            } else {
                                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The Secret Key doesn't match " +
                                        "with the Account");
                            }
                        } else if (savingRepository.existsById(id)) {
                            if (savingRepository.findById(id).get().getSecretKey().equals(secretKey)) {

                                Account account = accountRepository.findById(id).get();
                                account.getBalance().increaseAmount(amount.getAmount());
                                accountRepository.save(account);

                            } else {
                                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The Secret Key doesn't match " +
                                        "with the Account");
                            }
                        } else {
                            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "You can't send to a Credit Card");
                        }
                    } else {
                        throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The receiver's account is frozen");
                    }
                } else {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don´t have access to the Third Party account");
                }
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "It doesn't exist any Third Party with this hashed key");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The Account Id of the receiver doesn't exist");
        }
    }
}

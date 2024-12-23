package com.example.bankapp.service;

import com.example.bankapp.model.Account;
import com.example.bankapp.model.Transaction;
import com.example.bankapp.repository.AccountRepository;
import com.example.bankapp.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Service
public class AccountService  implements UserDetailsService {

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    public Account findAccountByUsername(String username) {
        return accountRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Account not found with the username: " + username));
    }

    public Account registerAccount(String username, String password) {
        if (accountRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Account already exists with the username: " + username);
        }
        Account account = new Account();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(password));
        account.setBalance(BigDecimal.ZERO);
        return accountRepository.save(account);
    }

    public void deposit(Account account, BigDecimal amount) {
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction transaction = new Transaction(
                amount, "Deposit", LocalDateTime.now(), account
        );
        transactionRepository.save(transaction);
    }

    public void withdraw(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance, your current balance is " + account.getBalance() +
                    " but you are trying to withdraw " + amount);
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction transaction = new Transaction(
                amount, "Widrawn an amount of " + amount, LocalDateTime.now(), account
        );
        transactionRepository.save(transaction);
    }

    public List<Transaction> getTransactionHistory(Account account) {
        Long accountId = account.getId();
        return transactionRepository.findByAccountId(accountId);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = findAccountByUsername(username);
        if (account == null) {
            throw new UsernameNotFoundException("Account not found with the username: " + username);
        }

        return new Account(account.getUsername(), account.getPassword(), account.getBalance(), account.getTransactions(), authorities());
    }

    public Collection<? extends GrantedAuthority> authorities(){
        return Arrays.asList(new SimpleGrantedAuthority("User"));
    }

    public void transferAmount(Account senderAccount, String receiverUsername, BigDecimal amountToTransfer){
        if (senderAccount.getBalance().compareTo(amountToTransfer) < 0) {
            throw new RuntimeException("Insufficient balance, your current balance is " + senderAccount.getBalance() +
                    " but you are trying to transfer " + amountToTransfer);
        }

        Account receiverAccount = accountRepository.findByUsername(receiverUsername)
                .orElseThrow(() -> new RuntimeException("Account not found with the username: " + receiverUsername));

        // SENDER
        senderAccount.setBalance(senderAccount.getBalance().subtract(amountToTransfer));
        accountRepository.save(senderAccount);

        // RECEIVER
        receiverAccount.setBalance(receiverAccount.getBalance().add(amountToTransfer));
        accountRepository.save(receiverAccount);

        Transaction debitTransaction = new Transaction(
                amountToTransfer,
                "Debited with money of " + amountToTransfer + " to " + receiverAccount.getUsername(),
                LocalDateTime.now(),
                senderAccount
        );
        transactionRepository.save(debitTransaction);

        Transaction creditTransaction = new Transaction(
                amountToTransfer,
                "Credited with money of " + amountToTransfer + " from " + senderAccount.getUsername(),
                LocalDateTime.now(),
                senderAccount
        );
        transactionRepository.save(creditTransaction);
    }
}

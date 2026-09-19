\# INC-2026-09-11 — Amount Extraction Incident



\## Reproduction



The affected bank message contained:



`Rs.5 debited from a/c \*\*4821 on 04-07-26 at 07:19 to UPI/WATER CAN. Avl Bal: Rs.92,213.10.`



The customer transaction amount was ₹5.00, but the historical ledger contained ₹92,213.10.



\## Root Cause



The amount extraction logic treated a rupee-looking number in the SMS as the transaction amount.



The affected message contained an integer transaction amount followed by a decimal available balance. The parser incorrectly selected the available balance instead of the transaction amount.



\## Blast Radius



The defect could affect bank SMS messages where the transaction amount is an integer and an available/current balance containing a decimal amount appears later in the same message.



The issue was therefore not limited to the specific ₹5 transaction.



\## Fix



The amount parser was changed to:



1\. Accept transaction amounts with or without decimal places.

2\. Detect balance context such as `Avl Bal`, `Available Balance`, `BalAvl`, and `Avl Limit`.

3\. Skip balance amounts when searching for the transaction amount.

4\. Preserve exact money values using `BigDecimal`.



\## Regression Test



Added a regression test covering an integer transaction amount followed by a decimal available balance.



The test verifies that the parser extracts:



`5.00`



instead of:



`92213.10`



\## Validation



`gradle test` passes after the fix.


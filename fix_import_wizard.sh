#!/bin/bash
FILE="app/src/main/java/com/example/ui/screens/ImportWizardScreen.kt"

# Simply remove the last SummaryRow duplicate. 
# We'll just delete from the SECOND @Composable fun SummaryRow to the end of the file.
sed -i '/@Composable/{
    $!N
    /fun SummaryRow/!{
        P;D
    }
}' $FILE # wait, simpler way: python script

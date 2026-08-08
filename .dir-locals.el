((java-mode
    . ((c-file-style . "java")
       (c-basic-offset . 2)
       (eval . (progn
                 (c-set-offset 'arglist-intro '++)
                 (c-set-offset 'arglist-cont-nonempty '++)
                 (c-set-offset 'arglist-close 0))))))

grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

INTEGER : '0' | [1-9][0-9]* ;
ID : [$a-zA-Z_][$a-zA-Z_0-9]* ;

AND_OP : '&&' ;
OR_OP : '||' ;
COMP_OP : '==' | '!=' | '<' | '>' | '<=' | '>=';

MULTDIV : '*' | '/';
PLUSMINUS : '+' | '-';

WS : [ \t\n\r\f]+ -> skip ;
COMMENT : '/*' (COMMENT|.)*? '*/' -> skip ;
LINE_COMMENT  : '//' .*? '\n' -> skip ;

program
    : (importDeclaration)* classDeclaration EOF
    ;

importDeclaration
    : 'import' name+=ID ('.' name+=ID)* ';'
    ;

classDeclaration : 'class' className=ID ( 'extends' extendName=ID )? '{' ( varDeclaration )* ( methodDeclaration )* mainMethodDeclaration? ( methodDeclaration )* '}' ;

varDeclaration
    : type name=ID ('=' expression)? ';'
    ;

parameter
    : type name=ID
    ;

mainParam
    : type_='String[]' var=ID
    ;

ret : 'return' ( expression )? ;

methodDeclaration
    : ('public' | 'private' | 'protected' | 'default')? typeReturn=type name=ID '(' (parameter (',' parameter)*)? ')' '{' (varDeclaration)* (statement)* (ret)?'}'
    ;

mainMethodDeclaration
    :  ('public')? 'static' 'void' name='main' '(' mainParam ')' '{' (varDeclaration)* (statement)* '}'
    ;

type locals[boolean isArray=false]
    : type_='int' #IntType
    | type_='int[]' {$isArray=true;} #IntArrayType
    | type_='boolean' #BooleanType
    | type_='String[]' {$isArray=true;} #StringType
    | type_=ID #ObjectType
    ;

statement
    : '{' (statement)* '}' #Block
    | 'if' '(' expression ')' statement ('else' statement)? #IfElse
    | 'while' '(' expression ')' statement #While
    | expression ';' #Stmt
    | id=ID '=' expression ';' #Assignment
    | id=ID '[' expression ']' '=' expression ';' #ArrayAssignment
    ;

expression
    : value=('true' | 'false') #Boolean
    | expression '[' expression ']' #ArrayAccess
    | expression '.' method=ID '(' ( expression (',' expression)* )? ')' #MethodCall
    | method=ID '(' ( expression (',' expression)* )? ')' #MethodCall
    | ret #ReturnStmt
    | value=INTEGER #Integer
    | id=ID #Variable
    | 'this' ('.' expression)* #This
    | '(' expression ')' #Parenthesis
    | expression '.' 'length' #ArrayLength
    | 'new' id=ID '(' ')' #NewObject
    | 'new' 'int' '[' size=expression ']' #ArrayInit
    | '!'expression #UnaryOp
    | expression op=MULTDIV expression #BinaryOp
    | expression op=PLUSMINUS expression #BinaryOp
    | expression op=COMP_OP expression #RelationalOp
    | expression op=AND_OP expression #RelationalOp
    | expression op=OR_OP expression #RelationalOp
    ;

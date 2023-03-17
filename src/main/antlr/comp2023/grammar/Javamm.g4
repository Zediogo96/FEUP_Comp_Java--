grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

INTEGER : [0]|([1-9][0-9]*) ;
ID : [a-zA-Z$_][a-zA-Z_$0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

COMMENT: '//' ~[\r\n]* -> skip ;

MULTI_LINE_COMMENT : '/*' .*? '*/' -> skip ;

program : (importDeclaration)* (classDeclaration)+ EOF ;

// import declaration that allows empty string
importDeclaration : 'import' name+=ID ('.' name+=ID)* ';' ;

classDeclaration : 'class' className=ID ( 'extends' extendName=ID )? '{' ( varDeclaration )* ( methodDeclaration )* mainMethodDeclaration? ( methodDeclaration )* '}' ;

varDeclaration : (type name=ID ('=' expression)? ';' ) ;

type : name = 'int' '[' ']' #array
    | name = 'boolean' #boolean
    | name = 'int' #int
    | name = 'String' #string
    | name = ID #class
    ;

parameter : type name=ID ;

mainMethodDeclaration : ('public')? 'static' 'void' 'main' '(' 'String' '[' ']' name=ID ')' '{' ( varDeclaration )* ( statement )* '}' ;

methodDeclaration :
    ('public' | 'private' | 'protected')? type name=ID '(' ( parameter ( ',' parameter )* )? ')' '{' ( varDeclaration )* ( statement )* returnStatement? '}' ;

returnStatement : 'return' expression?;

statement
    : '{' ( statement )* '}'
    | 'if' expression ( statement* ) ( 'else' ( statement* ) )?
    | returnStatement ';'?
    | 'while' '(' expression ')' ( statement )
    | 'System.out.println' '(' expression ')' ';'
    | ID '=' expression ';'
    | expression '[' expression ']' '=' expression ';'
    | expression '.' 'length' '=' expression ';'
    | expression ';'
    ;

expression
    : 'new' 'int' '[' expression ']'
    | 'new' ID '(' ')'
    | '!' expression
    | '(' expression ')'
    | expression op = ('*' | '/') expression
    | expression op = ('+' | '-') expression
    | expression op = ('<' | '>' | '>=' | '<=' | '&&' | '||') expression
    | expression '[' expression ']'
    | expression '.' 'length'
    | expression '.' ID '(' ( expression ( ',' expression )* )? ')'
    | value=INTEGER
    | value=ID
    | 'true'
    | 'false'
    | 'this'
    ;


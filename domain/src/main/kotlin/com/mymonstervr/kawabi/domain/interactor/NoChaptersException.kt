package com.mymonstervr.kawabi.domain.interactor

/** A source returned zero chapters -- treated as an error, not "all chapters removed". */
class NoChaptersException : Exception()

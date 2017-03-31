var JSJoda = {};
JSJoda.DateTimeFormatter = {}
JSJoda.DateTimeFormatter.ofPattern = function (pattern){}
JSJoda.LocalTime = {}
JSJoda.LocalTime.from = function(msg){}
JSJoda.LocalTime.MIDNIGHT = {}
JSJoda.LocalTime.prototype.compareTo = function(B){}
JSJoda.LocalTime.prototype.truncatedTo = function(B){}
JSJoda.LocalTime.prototype.plusHours = function(B){}
JSJoda.LocalTime.prototype.equals = function(anotherTime){}

JSJoda.Instant = {}
JSJoda.Instant.ofEpochMilli = function(millis){}
JSJoda.Instant.ofEpochSecond = function(millis){}
JSJoda.Instant.prototype.epochSecond = function() {}

JSJoda.LocalDate = {}
JSJoda.LocalDate.ofInstant = function(instant){}
JSJoda.LocalDate.prototype.dayOfWeek = function() {}
JSJoda.LocalDate.prototype.plusDays = function(days) {}
JSJoda.LocalDate.prototype.atTime = function(time) {}
JSJoda.LocalDate.prototype.until = function(anotherDate) {}
JSJoda.LocalDate.prototype.equals = function(anotherDate) {}
JSJoda.LocalDate.prototype.dayOfMonth = function() {}
JSJoda.LocalDate.prototype.month = function() {}
JSJoda.LocalDate.prototype.year = function() {}

JSJoda.LocalDateTime = {}
JSJoda.LocalDateTime.prototype.atZone = function(zone){}

JSJoda.ZonedDateTime = {}
JSJoda.ZonedDateTime.prototype.toInstant = function(){}

JSJoda.DayOfWeek = {}
JSJoda.DayOfWeek.MONDAY = {}

JSJoda.ChronoUnit = {}
JSJoda.ChronoUnit.HOURS = {}
JSJoda.ChronoUnit.MINUTES = {}
JSJoda.ChronoUnit.DAYS = {}
JSJoda.ChronoUnit.SECONDS= {}

JSJoda.Duration = {}
JSJoda.Duration.ofDays = function(days){}
JSJoda.Duration.ofHours = function(days){}
JSJoda.Duration.ofMinutes = function(days){}
JSJoda.Duration.prototype.toMinutes = function() {}
JSJoda.Duration.prototype.toHours = function() {}

JSJoda.ZoneId = {}
JSJoda.ZoneId.systemDefault = function(){}

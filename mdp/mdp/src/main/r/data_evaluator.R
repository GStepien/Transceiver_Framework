# MDP: A motif detector and predictor.
#
#    Copyright (c) 2018 Grzegorz Stepien
#
#    This program is free software: you can redistribute it and/or modify
#    it under the terms of the GNU General Public License as published by
#    the Free Software Foundation, either version 3 of the License, or
#    (at your option) any later version.
#
#    This program is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#    GNU General Public License for more details.
#
#    You should have received a copy of the GNU General Public License
#    along with this program.  If not, see <https://www.gnu.org/licenses/>.
# 
#    For more details, see './LICENSE.md'
#    (where '.' represents this program's root directory).

#' This script generates a number of plots based on a configuration data structure.

#' Randomly initialize the .Random.seed field (does not exist by default when run in console via `Rscript` command)
set.seed(round((as.numeric(Sys.time()) * 1000) %% .Machine$integer.max))

packages <- c("rprojroot")
for(pkg in packages){
  if(!require(pkg, character.only = TRUE)){
    install.packages(pkg, dependencies = TRUE, repos = "http://cran.us.r-project.org")
    library(pkg, character.only = TRUE)
  }
}
thisfile1 <- normalizePath(thisfile())
thisfolder <- dirname(thisfile1)
oldwd <- getwd()
setwd(dir = file.path(thisfolder, "..", "..","..", "submodules", "SDG"))
source(file =  file.path("utils", "utils_color.R"))
utils_color_env <- get_utils_color_env()
setwd(dir = oldwd)

#' Note: workdir must be set to root dir of experiment
evaluate_all <- function(options){
  old_wd <- getwd()
  common_opts <- list()
  if("common_options" %in% names(options)){
    common_opts <- options[["common_options"]]
    options[["common_options"]] <- NULL
  }
  
  for(name in names(options)){
    print(paste0("Evaluating ", name))
    
    current_options <- modifyList(common_opts, options[[name]], keep.null = TRUE)
    stopifnot(length(current_options) == length(names(current_options)))
    stopifnot(length(current_options) == length(unique(names(current_options))))
    stopifnot(!("common_options" %in% names(current_options)))
    
    ordered_group_names <- vector(mode = "character", length = length(current_options[["group_by"]]))
    for(group_name in names(current_options[["group_by"]])){
      ordered_group_names[[current_options[["group_by"]][[group_name]]$order]] <- group_name
    }
    stopifnot(all(ordered_group_names != ""))
    ordered_group_list <- list()
    if(length(ordered_group_names) > 0){
      for(num in 1:length(ordered_group_names)){
        ordered_group_list[[ordered_group_names[[num]]]] <- current_options[["group_by"]][[ordered_group_names[[num]]]]
      }
    }
    current_options[["group_by"]] <- ordered_group_list
    setwd(dir = current_options[["root_dir"]])
    evaluate(current_options)
  }
  setwd(old_wd)
}

evaluate <- function(options){
  env <- new.env()
  for(option in names(options)){
    assign(x = option, value = options[[option]], envir = env)
  }
  
  evalq(expr = {
    #' Install (if necessary) and load required packages
    for(package in packages){
      if(!require(package, character.only = TRUE)){
        install.packages(package)
        stopifnot(require(package, character.only = TRUE))
      }
    }
    
    read_data <- function(key, source){
      stopifnot("type" %in% names(source))
      if(source[["type"]] == "csv"){ #' csv case
        stopifnot(all(c("rows", "file", "selector") %in% names(source)))
        stopifnot(is.null(source[["selector"]]) || is.function(source[["selector"]]))
        
        
        data <- read.csv(file = file.path(".", key, source[["file"]]), check.names = FALSE)
        if(!is.null(source[["rows"]])){
          data <- data[source[["rows"]]]
        }
        
        if(!is.null(source[["selector"]])){
          data <- source[["selector"]](data)
        }
        data <- as.list(data)
        
        setNULL = sapply(1:length(data), function(i) {
          return(length(data[[i]]) == 0)
        })
        if(any(setNULL)){
          warning(paste0("Encountered value columns with no data. Key: \"", key, "\", CSV columns: ", toString(source[["rows"]][setNULL])), immediate. = TRUE)
        }
        stopifnot(length(setNULL) == length(data))
        data[setNULL] <- NULL
        
      } else if(source[["type"]] == "json"){ #' json case
        stopifnot(all(c("json_param_name", "file") %in% names(source)))
        data <- fromJSON(file = file.path(".", key, source[["file"]]))
        for(json_key in source[["json_param_name"]]){
          data <- data[[json_key]]
        }
        data <- list(data)
      } else if(source[["type"]] == "lm-transitions"){
        stopifnot(all(c("file") %in% names(source)))
        lm <- fromJSON(file = file.path(".", key, source[["file"]]))
        data <- 0
        for(state in names(lm[["states"]])){
          data <- data + length(lm[["states"]][[state]])
        }
        data <- list(data)
      } else{
        stop("Unknown source format.")
      }
      
      if("round_digits" %in% names(source)){
        data <- lapply(data, FUN = round, digits = source[["round_digits"]])
      }
      
      return(data)
    }
    
    #' Load data
    keys <- list.dirs(path = ".", full.names = FALSE, recursive = FALSE)
    keys <- grep(pattern = dir_pattern, x = keys, perl = TRUE, value = TRUE)
    data <- list()
    for(key in keys){
      data[[key]] <- read_data(key, source = x_axis)
      
      if(length(data[[key]]) == 0 || length(data[[key]][[1]]) == 0){
        warning(paste0("No x-values present for key: \"", key, "\". Dropping key altogether."), immediate. = TRUE)
        data[[key]] <- NULL
      }
      else {
        stopifnot(length(data[[key]]) == 1)
        data[[key]] <- append(data[[key]], read_data(key, source = y_axis))
        stopifnot(length(data[[key]]) >= 1)
        
        if(length(data[[key]]) == 1){
          warning(paste0("No y-values present for key: \"", key, "\". Dropping key altogether."), immediate. = TRUE)
          data[[key]] <- NULL
        }     
      }
    }
    
    if(length(data) == 0){
      warning("No data present in current experiment. Returning.")
      return()
    }
    
    stopifnot(all(sapply(data, FUN = function(dataset){
      stopifnot(is.list(dataset))
      num_cols <- length(dataset)
      stopifnot(num_cols >= 2)
      return(all(sapply(2:num_cols, FUN = function(i){
        return(length(dataset[[1]]) == length(dataset[[i]]))
      })))
    })))
    
    #' Group data
    #' Compute key-equivalence classes w.r.t. group_by and the y-columns assigned to each key
    if(!exists("group_by")){
      group_by <- list()
    }
    equivalence_classes <- list()
    legend_names <- list()
    for(key in keys){
      for(y_col_index in 2:length(data[[key]])){
        group_key_values <- list()
        if(length(data[[key]]) > 2){
          group_key_values <- append(group_key_values, y_axis[["row_group_legend_values"]][[y_col_index - 1]])
        }
        
        for(group_key in group_by){
          group_key_values <- append(group_key_values, read_data(key = key, source = group_key))
        }
        group_key_string <- toString(group_key_values)
        if(!(group_key_string %in% names(legend_names))){
          legend_name <- list()
          i <- 1
          values <- list()
          if(length(data[[key]]) > 2){
            values <- append(values, group_key_values[[i]])
            legend_name <- append(legend_name, y_axis[["row_group_legend_name"]])
            i <- i + 1
          }
          
          for(group_key in group_by){
            values <- append(values, group_key_values[[i]])
            legend_name <- append(legend_name, group_key[["legend_name"]])
            i <- i + 1
          }
          
          stopifnot(length(values) == i - 1)
          stopifnot(length(values) == length(legend_name))
          stopifnot(length(values) == length(group_by) + if(length(data[[key]]) > 2) 1 else 0)
          #stopifnot(length(legend_name) > 0)
          
          empty_legend_names <- (legend_name == "")
          values[empty_legend_names] <- NULL
          legend_name[empty_legend_names] <- NULL
          stopifnot(length(values) == length(legend_name))
          
          n <- length(legend_name)
          legend_name <- paste0(legend_name, collapse = ", ")
          values <- paste0(values, collapse = ", ")
          if(n > 1){
            legend_name <- paste0("(", legend_name,")")
            values <- paste0("(", values, ")")
          }
          if(n > 0){
            legend_names[[group_key_string]] <- paste0(legend_name, " = ", values)
          }
        }
        
        if(nchar(group_key_string) == 0){
          group_key_string <- " "
        }
        if(!(group_key_string %in% names(equivalence_classes))){
          equivalence_classes[[group_key_string]] <- list()
        }
        equivalence_classes[[group_key_string]] <- append(equivalence_classes[[group_key_string]], list(list(key, y_col_index)))
      }
    }
    
    grouped_data <- list()
    for(group_key in names(equivalence_classes)){
      for(key_tuple in equivalence_classes[[group_key]]){
        if(length(grouped_data[[group_key]]) == 0){
          grouped_data[[group_key]] <- list()
          grouped_data[[group_key]][[1]] <- data[[key_tuple[[1]]]][[1]]
          grouped_data[[group_key]][[2]] <- data[[key_tuple[[1]]]][[key_tuple[[2]]]]
        }
        else{
          grouped_data[[group_key]][[1]] <- rbind(grouped_data[[group_key]][[1]], data[[key_tuple[[1]]]][[1]])
          grouped_data[[group_key]][[2]] <- rbind(grouped_data[[group_key]][[2]], data[[key_tuple[[1]]]][[key_tuple[[2]]]])
        }
      }
      stopifnot(length(grouped_data[[group_key]]) == 2)
    }
    
    stopifnot(all(sapply(grouped_data, FUN = function(dataset){
      stopifnot(length(dataset) == 2)
      return(length(dataset[[1]]) == length(dataset[[2]]))
    })))
    
    data <- grouped_data
    # End of grouping 
    
    if(sort_x_values){
      for(key in names(data)){
        sorted_data <- sort(data[[key]][[1]], decreasing = FALSE, index.return = TRUE)
        data[[key]][[1]] <- sorted_data$x
        data[[key]][[2]] <- data[[key]][[2]][sorted_data$ix]
      }
    }
    
    if(x_remove_duplicates){
      for(key in names(data)){
        duplicate_x <- unique(data[[key]][[1]][duplicated(data[[key]][[1]])])
        for(dup_x in duplicate_x){
          x_indexes <- which(data[[key]][[1]] == dup_x)
          num_duplicates <- length(x_indexes)
          stopifnot(num_duplicates > 1)
          y <- x_remove_duplicates_strategy(dup_x, data[[key]][[2]][x_indexes])
          
          data[[key]][[2]][[x_indexes[[1]]]] <- y
          data[[key]][[1]] <- data[[key]][[1]][-x_indexes[2:num_duplicates]]
          data[[key]][[2]] <- data[[key]][[2]][-x_indexes[2:num_duplicates]]
        }
      }
      stopifnot(all(sapply(data, FUN = function(dataset){
        return(length(dataset[[1]]) == length(dataset[[2]]))
      })))
    }
    
    #' Get plot colors
    random_color <- utils_color_env$Random_Color_Iterator$new(assertions_status = FALSE,
                                                                    rnd_seed = rnd_color_seed,
                                                                    saturation = 1.0,
                                                                    value = 0.8)
    
    colors <- unlist(random_color$get_next(length(data)))
    #' Increase saturation
    colors <- sapply(colors,
                     FUN = function(rgb_color){
                       rgb_color <- col2rgb(rgb_color)
                       hsv_color <- rgb2hsv(rgb_color)
                       stopifnot(hsv_color[[2]] > 0)
                       hsv_color[[2]] <- hsv_color[[2]] + 0.5*(1-hsv_color[[2]])
                       
                       return(hsv(hsv_color[[1]],
                                  hsv_color[[2]],
                                  hsv_color[[3]]))
                     })
    names(colors) <- names(data)
   
    get_range <- function(data, index){
      return(
        c(
          min(
            sapply(data, FUN = function(subdata){
              return(min(subdata[[index]]))
            })),
          max(
            sapply(data, FUN = function(subdata){
              return(max(subdata[[index]]))
            }))))
    }
    
    get_axis_params <- function(range, num_ticks, labels_every_tick){
      magnitude <- 10^ceiling(log10(x = range[[2]] - range[[1]]))
      tick_width <- magnitude / num_ticks
      label_width <- tick_width * labels_every_tick
      min <- floor((range[[1]]) / label_width) * label_width
      max <- ceiling((range[[2]]) / label_width) * label_width
      
      tick_seq <- seq(min, max, by = tick_width)
      
      label_seq <- prettyNum(seq(min, max, by = label_width))
      
      return(list(ticks = tick_seq,
                  labels = label_seq,
                  magnitude = magnitude,
                  tick_width = tick_width,
                  label_width = label_width,
                  min = min,
                  max = max))
    }
    
    X11(
      width = x_axis[["plot_width"]] / 72,
      height = y_axis[["plot_height"]] / 72,
      title = window_title)
    
    if(x_is_numeric){
      if("limits" %in% names(x_axis) && !is.null(x_axis[["limits"]])){
        x_range <- x_axis[["limits"]]
      }
      else{
        x_range <- get_range(data, 1)
      }
      stopifnot(x_range[[1]] <= x_range[[2]])
      x_axis_params <- get_axis_params(range = x_range, num_ticks = x_axis[["num_ticks"]], labels_every_tick = x_axis[["labels_every_tick"]])
      x_tick_seq <- x_axis_params[["ticks"]]
      x_label_seq <- x_axis_params[["labels"]]
      x_axis_ticks_params <- list(side = 1, at = setdiff(x_tick_seq,x_label_seq), labels = FALSE, tcl=-0.25)
      x_axis_label_seq_params <- list(side = 1, at = x_label_seq, labels = x_label_seq, las = 0, tcl=-0.5, cex.axis = x_axis[["label_size"]])
      
      plot_pars <- list(1, 
                        type = "n", 
                        xlab = "", 
                        ylab = "",
                        xaxt = "n",
                        yaxt = "n",
                        xlim = c(x_tick_seq[[1]], tail(x_tick_seq, n = 1)))
      
      if(y_is_numeric){
        if("limits" %in% names(y_axis) && !is.null(y_axis[["limits"]])){
          y_range <- y_axis[["limits"]]
        }
        else{
          y_range <- get_range(data, 2)
        }
        stopifnot(y_range[[1]] <= y_range[[2]])
        
        #' Plot points
        y_axis_params <- get_axis_params(range = y_range, num_ticks = y_axis[["num_ticks"]], labels_every_tick = y_axis[["labels_every_tick"]])
        y_tick_seq <- y_axis_params[["ticks"]]
        y_label_seq <- y_axis_params[["labels"]]
        y_axis_ticks_params <- list(side = 2, at = setdiff(y_tick_seq,y_label_seq), labels = FALSE, tcl=-0.25)
        y_axis_label_seq_params <- list(side = 2, at = y_label_seq, labels = y_label_seq, las = 0, tcl= -0.5, cex.axis = y_axis[["label_size"]])
        
        plot_pars <- append(
          plot_pars,
          list(ylim = c(y_tick_seq[[1]], tail(y_tick_seq, n = 1))))
        
        legend_names <- mixedsort(unlist(legend_names))
        max_legend_name_length = if(length(legend_names) == 0) 0 else max(sapply(legend_names, nchar))
        par(mar = par()$mar + c(0,0,0,1 + legend_size * max_legend_name_length * 0.4))
        par(mgp = par()$mgp + c(-0.8,-0.2,0))
        do.call("plot", plot_pars)
        par(new=TRUE)
        
        do.call("axis", x_axis_ticks_params)
        do.call("axis", y_axis_ticks_params)
        
        do.call("axis", x_axis_label_seq_params)
        do.call("axis", y_axis_label_seq_params)
        
        abline(v=x_label_seq, lty=3, col="#CCCCCC")
        abline(h=y_label_seq, lty=3, col="#CCCCCC")
        
        for(group_name in names(data)){
          points(x = data[[group_name]][[1]],
                 y = data[[group_name]][[2]],
                 col = colors[[group_name]],
                 bg = colors[[group_name]],
                 pch = pt_type,
                 cex = pt_size)
        }
        
        if(is_continuous){
          #' Plot lines between points
          for(group_name in names(data)){
            num_elems <- length(data[[group_name]][[1]])
            if(num_elems <= 1){
              next
            }
            segments(x0 = data[[group_name]][[1]][1:(num_elems - 1)], 
                     y0 = data[[group_name]][[2]][1:(num_elems - 1)], 
                     x1 = data[[group_name]][[1]][2:num_elems], 
                     y1 = data[[group_name]][[2]][2:num_elems], 
                     col = colors[[group_name]],
                     lwd = l_width)
          }
        }
        if(length(legend_names) > 0){
          x_tail <- tail(x_tick_seq, n = 1)
          x_head <- head(x_tick_seq, n = 1)
          legend(x = x_tail + (x_tail - x_head)/100,#x_axis_params[["tick_width"]],
                 y = tail(y_tick_seq, n = 1),
                 legend = legend_names,
                 col = colors[names(legend_names)],
                 pt.bg = colors[names(legend_names)],
                 lwd = l_width,
                 pch = pt_type,
                 cex = legend_size,
                 bg = "#F2F2F2",
                 xpd = TRUE)
        }
        title(main = main_title, line=0.8, cex.main = main_title_size)
        title(xlab = x_axis[["title"]], cex.lab = x_axis[["title_size"]])
        title(ylab = y_axis[["title"]], cex.lab = y_axis[["title_size"]])
        
      } else {
        stopifnot(!is_continuous)
        
        #' TODO
        stop("Not implemented yet.")
      }
    } else {
      stopifnot(!is_continuous)
      if(y_is_numeric){
        
        #' TODO
        stop("Not implemented yet.")
      } else {
        
        #' TODO
        stop("Not implemented yet.")
      }
    }
    
    dir <- dirname(plot_out_path)
    if(!exists(dir)){
      dir.create(path = dir, showWarnings = FALSE, recursive = TRUE)
    }
    
    dev.print(svg, plot_out_path,
              width = x_axis[["plot_width"]] / 72, 
              height = y_axis[["plot_height"]] / 72,
              pointsize = 12,
              antialias = "subpixel")
    
    dev.off()
  }, env = env)
}

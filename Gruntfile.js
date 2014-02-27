module.exports = function(grunt) {

  // Project configuration.
  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    uglify: {
      options: {
        banner: '/*! <%= pkg.name %> <%= grunt.template.today("yyyy-mm-dd") %> */\n'
      },
      build: {
        src: [
          'resources/components/jquery/jquery.js',
          'resources/components/underscore/underscore.js',
          'resources/components/bootstrap/docs/assets/js/bootstrap.js',
          'resources/components/bootstrap/docs/assets/js/bootstrap-typeahead.js',
          'resources/components/bootstrap/docs/assets/js/bootstrap-tooltip.js',
          'resources/components/bootstrap/docs/assets/js/bootstrap-popover.js',
          'resources/components/jquery-textrange/jquery-textrange.js',
          'resources/components/localforage/dist/localforage.js',
          'resources/static/js/<%= pkg.name %>.js'
        ],
        dest: 'resources/static/js/<%= pkg.name %>.min.js'
      }
    },
    recess: {
      dist: {
        src: ['resources/components/bootstrap/less/bootstrap.less',
              'resources/static/css/data-api.less'],
        dest: 'resources/static/css/data-api.min.css',
        options: {
          compile: true,
          compress: true
        }
      }
    },
    watch: {
      files: ['Gruntfile.js', 'resources/components/**/*', 'resources/static/**/*'],
      tasks: ['default']
    }
  });

  grunt.loadNpmTasks('grunt-recess');
  grunt.loadNpmTasks('grunt-contrib-uglify');
  grunt.loadNpmTasks('grunt-contrib-watch');

  // Default task.
  grunt.registerTask('default', ['recess', 'uglify']);
};

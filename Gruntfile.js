module.exports = function(grunt) {

  // Project configuration.
  grunt.initConfig({
      pkg: grunt.file.readJSON('package.json'),
      copy: {
        clientsidejs: {
          files:[{
            expand:true,
            cwd: 'node_modules/jquery/dist/',
            src: '*',
            dest: 'resources/public/dist/external/jquery/'
          }, {
            expand:true,
            cwd: 'node_modules/bootstrap-datepicker/dist/',
            src: '**/*',
            dest: 'resources/public/dist/external/bootstrap-datepicker/'
          },{
            expand:true,
            cwd: 'node_modules/bootstrap/dist/',
            src: '**/*',
            dest: 'resources/public/dist/external/bootstrap/'
          },{
            expand:true,
            cwd: 'node_modules/js-joda/dist/',
            src: '**/*',
            dest: 'resources/public/dist/external/js-joda/'
          },{
            expand:true,
            cwd: 'node_modules/font-awesome/',
            src: ['css/**/*','fonts/**/*'],
            dest: 'resources/public/dist/external/font-awesome/'
          }]
        }
      }
  });

  grunt.loadNpmTasks('grunt-contrib-copy');
  // Default task(s).
  grunt.registerTask('default', "log", ()=>grunt.log.write("Use the copy task"));

};
